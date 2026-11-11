package com.rental.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.platform.config.UserContextHolder;
import com.rental.platform.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.*;
import java.security.*;
import java.math.BigDecimal;
import java.util.*;

/** Private evidence is never placed in the public upload directory or sent to an AI model. */
@Service
public class HousingTrustService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final PlatformWorkflowService flow;
    private final Path root = Path.of("data", "trust-private").toAbsolutePath().normalize();
    private byte[] key;
    public HousingTrustService(JdbcTemplate db, ObjectMapper json, PlatformWorkflowService flow) { this.db=db; this.json=json; this.flow=flow; }
    @PostConstruct public void initialize() throws Exception {
        Files.createDirectories(root);
        Path keyPath=root.resolve("storage.key");
        if(!Files.exists(keyPath)) { byte[] generated=new byte[32];new SecureRandom().nextBytes(generated);Files.write(keyPath,generated,StandardOpenOption.CREATE_NEW); }
        key=Files.readAllBytes(keyPath);
        db.execute("""
          CREATE TABLE IF NOT EXISTS t_trust_file (
           id VARCHAR(36) PRIMARY KEY,owner_id BIGINT NOT NULL,house_id BIGINT NULL,kind VARCHAR(20) NOT NULL,
           mime VARCHAR(80) NOT NULL,bytes BIGINT NOT NULL,sha256 VARCHAR(64) NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
           KEY idx_trust_file_owner(owner_id),KEY idx_trust_hash(sha256)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
          """);
        db.execute("""
          CREATE TABLE IF NOT EXISTS t_house_trust (
           house_id BIGINT PRIMARY KEY,owner_id BIGINT NOT NULL,publisher_type VARCHAR(20) NOT NULL,applicant_name VARCHAR(80) NOT NULL,
           identity_id VARCHAR(36) NOT NULL,property_id VARCHAR(36) NOT NULL,authorization_id VARCHAR(36),photo_ids TEXT NOT NULL,
           deposit_months INT NOT NULL,payment_months INT NOT NULL,service_fee DECIMAL(12,2) NOT NULL,other_fees VARCHAR(1000) NOT NULL,
           utility_terms VARCHAR(1000) NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'PENDING',review_note VARCHAR(1000),
           reviewed_by BIGINT,reviewed_at DATETIME,submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
           confirmed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,consent_version VARCHAR(40) NOT NULL,
           KEY idx_trust_status(status,submitted_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
          """);
        db.execute("""
          CREATE TABLE IF NOT EXISTS t_house_report (
           id BIGINT AUTO_INCREMENT PRIMARY KEY,house_id BIGINT NOT NULL,reporter_id BIGINT NOT NULL,category VARCHAR(40) NOT NULL,
           content VARCHAR(2000) NOT NULL,evidence_ids TEXT NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
           response VARCHAR(1000),handled_by BIGINT,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,handled_at DATETIME,
           KEY idx_report_owner(reporter_id,created_at),KEY idx_report_status(status,created_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
          """);
        db.execute("""
          CREATE TABLE IF NOT EXISTS t_trust_audit (
           id BIGINT AUTO_INCREMENT PRIMARY KEY,house_id BIGINT,actor_id BIGINT NOT NULL,action VARCHAR(40) NOT NULL,
           note VARCHAR(1000) NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
           KEY idx_audit_house(house_id,id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
          """);
    }
    public static long number(Object value) { return ((Number)value).longValue(); }
    public static String text(Map<String,Object> body,String key,int max) {
        String value=Objects.toString(body.get(key),"").trim();
        if(value.isEmpty()||value.length()>max)throw new BusinessException(key+"不能为空且不能超过"+max+"字");return value;
    }
    public static void admin() { UserContextHolder.getCurrentUserId();if(!UserContextHolder.hasRole("ADMIN"))throw BusinessException.forbidden("仅管理员可审核"); }
    private Map<String,Object> house(long id,boolean lock) {
        var rows=db.queryForList("SELECT * FROM t_house WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty())throw BusinessException.notFound("房源不存在");return rows.get(0);
    }
    private void owner(Map<String,Object> house) {
        if(number(house.get("landlord_id"))!=UserContextHolder.getCurrentUserId())throw BusinessException.forbidden("只能处理本人发布的房源");
    }
    private void audit(long houseId,String action,String note) {
        db.update("INSERT INTO t_trust_audit(house_id,actor_id,action,note) VALUES(?,?,?,?)",houseId,UserContextHolder.getCurrentUserId(),action,note);
    }
    public Map<String,Object> upload(MultipartFile file,String kind) throws Exception {
        long uid=UserContextHolder.getCurrentUserId();
        if(!Set.of("IDENTITY","PROPERTY","AUTHORIZATION","PHOTO","EVIDENCE").contains(kind))throw new BusinessException("材料类型无效");
        if(file.isEmpty()||file.getSize()>8*1024*1024)throw new BusinessException("请上传不超过8MB的图片或PDF");
        if(db.queryForObject("SELECT COUNT(*) FROM t_trust_file WHERE owner_id=? AND created_at>DATE_SUB(NOW(),INTERVAL 1 HOUR)",Integer.class,uid)>=60)throw new BusinessException("上传过于频繁，请稍后重试");
        byte[] bytes=file.getBytes();String mime;
        if(bytes.length>=8 && Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))mime="image/png";
        else if(bytes.length>=3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255)mime="image/jpeg";
        else if(bytes.length>=5&&new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))mime="application/pdf";
        else throw new BusinessException("仅支持实际内容为JPG、PNG或PDF的文件");
        if(kind.equals("PHOTO")&&mime.equals("application/pdf"))throw new BusinessException("房源照片只能上传JPG或PNG");
        if(mime.startsWith("image/")&&javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes))==null)throw new BusinessException("图片无法读取");
        String id=UUID.randomUUID().toString();byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        byte[] encrypted=cipher.doFinal(bytes),payload=new byte[nonce.length+encrypted.length];System.arraycopy(nonce,0,payload,0,12);System.arraycopy(encrypted,0,payload,12,encrypted.length);
        Files.write(root.resolve(id+".enc"),payload,StandardOpenOption.CREATE_NEW);
        try { db.update("INSERT INTO t_trust_file(id,owner_id,kind,mime,bytes,sha256) VALUES(?,?,?,?,?,?)",id,uid,kind,mime,bytes.length,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))); }
        catch(RuntimeException e) { Files.deleteIfExists(root.resolve(id+".enc"));throw e; }
        return Map.of("id",id,"kind",kind,"mime",mime,"bytes",bytes.length);
    }
    public Map<String,Object> fileMetadata(String id,boolean publicPhoto) {
        var rows=db.queryForList("SELECT * FROM t_trust_file WHERE id=?",id);
        if(rows.isEmpty())throw BusinessException.notFound("材料不存在");var f=rows.get(0);
        if(publicPhoto) {
            if(!"PHOTO".equals(f.get("kind"))||f.get("house_id")==null)throw BusinessException.notFound("照片不可用");
            var h=house(number(f.get("house_id")),false);if(!"APPROVED".equals(h.get("status")))throw BusinessException.notFound("照片未公开");
        } else {
            long uid=UserContextHolder.getCurrentUserId();
            if(uid!=number(f.get("owner_id"))&&!UserContextHolder.hasRole("ADMIN"))throw BusinessException.forbidden("无权查看材料");
            if(UserContextHolder.hasRole("ADMIN"))db.update("INSERT INTO t_trust_audit(house_id,actor_id,action,note) VALUES(?,?,?,?)",f.get("house_id"),uid,"VIEW_MATERIAL","查看材料 "+id);
        }
        return f;
    }
    public byte[] readFile(String id) throws Exception {
        // Called only after fileMetadata has authorized the exact same ID.
        if(!id.matches("[a-f0-9-]{36}"))throw BusinessException.notFound("材料不存在");
        byte[] payload=Files.readAllBytes(root.resolve(id+".enc"));Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOf(payload,12)));
        return cipher.doFinal(Arrays.copyOfRange(payload,12,payload.length));
    }
    private String requiredFile(Object id,String kind,long uid,long houseId) {
        String value=Objects.toString(id,"");var rows=db.queryForList("SELECT * FROM t_trust_file WHERE id=? AND owner_id=? AND kind=? FOR UPDATE",value,uid,kind);
        if(rows.isEmpty())throw new BusinessException("请上传本人持有的"+kind+"材料");
        Object bound=rows.get(0).get("house_id");if(bound!=null&&number(bound)!=houseId)throw new BusinessException("材料已用于其他房源，请重新上传");return value;
    }
    public List<String> ids(Object value) {
        if(!(value instanceof List<?> list)||list.size()>12)throw new BusinessException("附件数量无效");
        return list.stream().map(Object::toString).distinct().toList();
    }
    private String encode(Object value) { try{return json.writeValueAsString(value);}catch(Exception e){throw new BusinessException("材料格式错误");} }
    public List<String> decode(Object value) { try{return json.readValue(value.toString(),new TypeReference<List<String>>(){});}catch(Exception e){return List.of();} }
    private int months(Object value,int max) { try { int n=Integer.parseInt(value.toString());if(n<0||n>max)throw new Exception();return n; }catch(Exception e){throw new BusinessException("押付月数无效");} }
    @Transactional public void submit(long houseId,Map<String,Object> body) {
        var h=house(houseId,true);owner(h);long uid=UserContextHolder.getCurrentUserId();
        String type=text(body,"publisherType",20);if(!Set.of("OWNER","AUTHORIZED").contains(type))throw new BusinessException("请选择产权人或受托出租人");
        if(!Boolean.TRUE.equals(body.get("consent")))throw new BusinessException("请阅读并单独确认材料审核告知");
        String name=text(body,"applicantName",80),identity=requiredFile(body.get("identityId"),"IDENTITY",uid,houseId),property=requiredFile(body.get("propertyId"),"PROPERTY",uid,houseId);
        String authorization=type.equals("AUTHORIZED")?requiredFile(body.get("authorizationId"),"AUTHORIZATION",uid,houseId):null;
        List<String> photos=ids(body.get("photoIds"));if(photos.size()<3||photos.size()>9)throw new BusinessException("请上传3至9张房源实拍照片");
        for(String photo:photos)requiredFile(photo,"PHOTO",uid,houseId);
        int deposit=months(body.get("depositMonths"),12),payment=months(body.get("paymentMonths"),12);if(payment==0)throw new BusinessException("付款周期至少1个月");
        BigDecimal fee;try{fee=new BigDecimal(Objects.toString(body.get("serviceFee"),""));if(fee.signum()<0||fee.compareTo(new BigDecimal("100000"))>0||fee.scale()>2)throw new Exception();}catch(Exception e){throw new BusinessException("服务费用须为0至100000元，最多两位小数");}
        String other=text(body,"otherFees",1000),utilities=text(body,"utilityTerms",1000);
        if(Objects.toString(h.get("address"),"").isBlank()||Objects.toString(h.get("district"),"").isBlank())throw new BusinessException("请先补全区域与地址");
        if(h.get("latitude")==null||h.get("longitude")==null)throw new BusinessException("请在编辑房源页面标记房屋地图位置");
        db.update("""
          INSERT INTO t_house_trust(house_id,owner_id,publisher_type,applicant_name,identity_id,property_id,authorization_id,photo_ids,deposit_months,payment_months,service_fee,other_fees,utility_terms,consent_version)
          VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE publisher_type=VALUES(publisher_type),applicant_name=VALUES(applicant_name),identity_id=VALUES(identity_id),property_id=VALUES(property_id),authorization_id=VALUES(authorization_id),photo_ids=VALUES(photo_ids),deposit_months=VALUES(deposit_months),payment_months=VALUES(payment_months),service_fee=VALUES(service_fee),other_fees=VALUES(other_fees),utility_terms=VALUES(utility_terms),status='PENDING',review_note=NULL,reviewed_by=NULL,reviewed_at=NULL,submitted_at=NOW(),confirmed_at=NOW(),consent_version=VALUES(consent_version)
          """,houseId,uid,type,name,identity,property,authorization,encode(photos),deposit,payment,fee,other,utilities,"manual-material-v1");
        List<String> all=new ArrayList<>(photos);all.add(identity);all.add(property);if(authorization!=null)all.add(authorization);
        for(String id:all)db.update("UPDATE t_trust_file SET house_id=? WHERE id=?",houseId,id);
        List<String> publicPhotos=photos.stream().map(id->"/api/trust/photos/"+id).toList();
        db.update("UPDATE t_house SET status='PENDING',cover_image=?,images=?,updated_at=NOW() WHERE id=?",publicPhotos.get(0),encode(publicPhotos),houseId);
        audit(houseId,"SUBMIT","提交出租资格材料与费用信息，等待人工审核");
    }
    public List<Map<String,Object>> mine() {
        return db.queryForList("SELECT h.id,h.title,h.city,h.status,h.price,h.updated_at AS updatedAt,t.status AS materialStatus,t.review_note AS reviewNote FROM t_house h LEFT JOIN t_house_trust t ON t.house_id=h.id WHERE h.landlord_id=? ORDER BY h.updated_at DESC LIMIT 200",UserContextHolder.getCurrentUserId());
    }
    public Map<String,Object> application(long houseId) {
        var h=house(houseId,false);if(!UserContextHolder.hasRole("ADMIN"))owner(h);
        var result=new LinkedHashMap<String,Object>();result.put("house",h);
        var rows=db.queryForList("SELECT * FROM t_house_trust WHERE house_id=?",houseId);result.put("application",rows.isEmpty()?null:rows.get(0));
        result.put("files",db.queryForList("SELECT id,kind,mime,bytes FROM t_trust_file WHERE house_id=?",houseId));
        result.put("history",db.queryForList("SELECT action,note,created_at AS createdAt FROM t_trust_audit WHERE house_id=? AND action<>'VIEW_MATERIAL' ORDER BY id DESC LIMIT 30",houseId));
        if(UserContextHolder.hasRole("ADMIN"))result.put("duplicatePhotos",db.queryForList("SELECT DISTINCT f.id,f.house_id AS houseId FROM t_trust_file f JOIN t_trust_file own ON own.sha256=f.sha256 WHERE own.house_id=? AND own.kind='PHOTO' AND f.kind='PHOTO' AND f.house_id IS NOT NULL AND f.house_id<>? LIMIT 30",houseId,houseId));
        return result;
    }
    public Map<String,Object> publicInfo(long houseId) {
        var h=house(houseId,false);if(!"APPROVED".equals(h.get("status")))throw BusinessException.notFound("房源未公开");
        var rows=db.queryForList("SELECT publisher_type AS publisherType,deposit_months AS depositMonths,payment_months AS paymentMonths,service_fee AS serviceFee,other_fees AS otherFees,utility_terms AS utilityTerms,status,reviewed_at AS reviewedAt,confirmed_at AS confirmedAt FROM t_house_trust WHERE house_id=?",houseId);
        var result=new LinkedHashMap<String,Object>();boolean verified=!rows.isEmpty()&&"APPROVED".equals(rows.get(0).get("status"));
        result.put("materialReviewed",verified);result.put("scope",verified?"出租身份、权属及授权材料已人工审核；未接入官方核验，不保证证件真伪或房屋现状。":"该房源尚未完成本平台出租资格材料审核。");
        result.put("demo",Objects.toString(h.get("description"),"").contains("仿真")||Objects.toString(h.get("title"),"").contains("[演示]"));
        if(verified)result.put("details",rows.get(0));return result;
    }
    public Map<String,Object> queue(String status,int page,String keyword) {
        admin();page=Math.max(1,page);String term="%"+Objects.toString(keyword,"").trim()+"%";
        String where=" WHERE (?='ALL' OR h.status=?) AND (h.title LIKE ? OR h.city LIKE ?)";
        long total=db.queryForObject("SELECT COUNT(*) FROM t_house h"+where,Long.class,status,status,term,term);
        var list=db.queryForList("SELECT h.id,h.title,h.city,h.status,h.price,t.status AS materialStatus,t.submitted_at AS submittedAt,t.publisher_type AS publisherType FROM t_house h LEFT JOIN t_house_trust t ON t.house_id=h.id"+where+" ORDER BY (h.status='PENDING') DESC,h.updated_at DESC,h.id DESC LIMIT 50 OFFSET ?",status,status,term,term,(page-1)*50);
        return Map.of("list",list,"total",total,"page",page,"size",50);
    }
    @Transactional public void review(long houseId,Map<String,Object> body) {
        admin();var h=house(houseId,true);String status=text(body,"status",20),note=text(body,"note",1000);
        if(!Set.of("APPROVED","REJECTED","OFFLINE").contains(status))throw new BusinessException("审核结果无效");
        if(status.equals(h.get("status")))throw new BusinessException("记录已处理，请刷新后再操作");
        if(status.equals("REJECTED")&&!"PENDING".equals(h.get("status")))throw new BusinessException("只能退回待审核的房源");
        if(status.equals("APPROVED")) {
            if(!"PENDING".equals(h.get("status")))throw new BusinessException("只能审核待审核的房源，请刷新记录");
            if(number(h.get("landlord_id"))==UserContextHolder.getCurrentUserId())throw new BusinessException("不能审核本人发布的房源");
            if(db.queryForObject("SELECT COUNT(*) FROM t_house_trust WHERE house_id=? AND status='PENDING'",Integer.class,houseId)!=1)throw new BusinessException("房东尚未提交完整材料，不能通过审核");
            if(!Boolean.TRUE.equals(body.get("identityChecked"))||!Boolean.TRUE.equals(body.get("propertyChecked"))||!Boolean.TRUE.equals(body.get("listingChecked")))throw new BusinessException("请逐项核对身份、出租资格及房源费用信息");
        }
        db.update("UPDATE t_house SET status=?,updated_at=NOW() WHERE id=?",status,houseId);
        db.update("UPDATE t_house_trust SET status=?,review_note=?,reviewed_by=?,reviewed_at=NOW() WHERE house_id=?",status,note,UserContextHolder.getCurrentUserId(),houseId);
        audit(houseId,"REVIEW_"+status,note);
        flow.notify(number(h.get("landlord_id")),"房源审核结果",h.get("title")+"："+status+"。"+note,"/app/workflow");
    }
    @Transactional public void withdraw(long houseId) {
        var h=house(houseId,true);owner(h);db.update("UPDATE t_house SET status='OFFLINE',updated_at=NOW() WHERE id=?",houseId);
        db.update("UPDATE t_house_trust SET status='WITHDRAWN',review_note='发布者主动下架' WHERE house_id=?",houseId);audit(houseId,"WITHDRAW","发布者主动下架");
    }
    @Transactional public void invalidate(long houseId) {
        db.update("UPDATE t_house_trust SET status='DRAFT',review_note='房源信息有修改，请重新提交材料审核',reviewed_at=NULL,reviewed_by=NULL WHERE house_id=?",houseId);
        audit(houseId,"EDIT","房源修改后退回待审核");
    }
    @Transactional public void report(long houseId,Map<String,Object> body) {
        long uid=UserContextHolder.getCurrentUserId();var h=house(houseId,true);
        if(!"APPROVED".equals(h.get("status")))throw new BusinessException("该房源已下架或未公开");
        if(number(h.get("landlord_id"))==uid)throw new BusinessException("本人房源请从我的房源下架或修改");
        String category=text(body,"category",40);if(!Set.of("虚假房源","图片不符","价格或费用不符","冒充房东","已出租仍展示","其他").contains(category))throw new BusinessException("举报类型无效");
        String content=text(body,"content",2000);if(content.length()<10)throw new BusinessException("请至少填写10字，说明具体情况");
        if(db.queryForObject("SELECT COUNT(*) FROM t_house_report WHERE house_id=? AND reporter_id=? AND status='PENDING'",Integer.class,houseId,uid)>0)throw new BusinessException("该房源已有待处理举报，请在我的举报中查看进度");
        List<String> evidence=ids(body.getOrDefault("evidenceIds",List.of()));for(String id:evidence)requiredFile(id,"EVIDENCE",uid,houseId);
        db.update("INSERT INTO t_house_report(house_id,reporter_id,category,content,evidence_ids) VALUES(?,?,?,?,?)",houseId,uid,category,content,encode(evidence));
    }
    public List<Map<String,Object>> reports(boolean all) {
        if(all)admin();long uid=UserContextHolder.getCurrentUserId();
        return db.queryForList("SELECT r.*,h.title AS houseTitle FROM t_house_report r LEFT JOIN t_house h ON h.id=r.house_id "+(all?"":"WHERE r.reporter_id=? ")+"ORDER BY (r.status='PENDING') DESC,r.id DESC LIMIT 200",all?new Object[]{}:new Object[]{uid});
    }
    @Transactional public void handleReport(long id,Map<String,Object> body) {
        admin();var initial=db.queryForList("SELECT house_id FROM t_house_report WHERE id=?",id);if(initial.isEmpty())throw BusinessException.notFound("举报不存在");
        long houseId=number(initial.get(0).get("house_id"));house(houseId,true);
        var report=db.queryForList("SELECT * FROM t_house_report WHERE id=? FOR UPDATE",id).get(0);
        if(!"PENDING".equals(report.get("status")))throw new BusinessException("举报已处理，请刷新记录");
        String status=text(body,"status",20),response=text(body,"response",1000);if(!Set.of("RESOLVED","DISMISSED").contains(status))throw new BusinessException("处理结果无效");
        if(Boolean.TRUE.equals(body.get("takeOffline"))) {
            if(!status.equals("RESOLVED"))throw new BusinessException("驳回举报不能同时下架房源");
            db.update("UPDATE t_house SET status='OFFLINE',updated_at=NOW() WHERE id=?",houseId);
            db.update("UPDATE t_house_trust SET status='OFFLINE',review_note=? WHERE house_id=?",response,houseId);
        }
        db.update("UPDATE t_house_report SET status=?,response=?,handled_by=?,handled_at=NOW() WHERE id=?",status,response,UserContextHolder.getCurrentUserId(),id);
        audit(houseId,"REPORT_"+status,"举报#"+id+"："+response);
        flow.notify(number(report.get("reporter_id")),"举报处理结果",response,"/app/workflow");
        if(Boolean.TRUE.equals(body.get("takeOffline")))flow.notify(number(house(houseId,false).get("landlord_id")),"房源因举报处理下架",response,"/app/workflow");
    }
}
