package com.rental.platform.service;

import com.rental.platform.config.UserContextHolder;
import com.rental.platform.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Moderation decisions and the corresponding user feedback commit together. */
@Service
public class PlatformWorkflowService {
    private final JdbcTemplate db;
    public PlatformWorkflowService(JdbcTemplate db) { this.db=db; }
    @PostConstruct public void initialize() {
        db.execute("CREATE TABLE IF NOT EXISTS t_platform_notice (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,title VARCHAR(120) NOT NULL,content VARCHAR(2000) NOT NULL,link VARCHAR(300) NOT NULL,read_at DATETIME,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,KEY idx_notice_user(user_id,id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        db.execute("CREATE TABLE IF NOT EXISTS t_platform_decision (id BIGINT AUTO_INCREMENT PRIMARY KEY,kind VARCHAR(20) NOT NULL,target_id BIGINT NOT NULL,actor_id BIGINT NOT NULL,status VARCHAR(30) NOT NULL,note VARCHAR(1000) NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,KEY idx_decision_target(kind,target_id,id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }
    public void notify(long uid,String title,String content,String link) {
        db.update("INSERT INTO t_platform_notice(user_id,title,content,link) VALUES(?,?,?,?)",uid,title,content,link);
    }
    public void record(String kind,long id,String status,String note) {
        db.update("INSERT INTO t_platform_decision(kind,target_id,actor_id,status,note) VALUES(?,?,?,?,?)",kind,id,UserContextHolder.getCurrentUserId(),status,note);
    }
    public List<Map<String,Object>> notices() { return db.queryForList("SELECT * FROM t_platform_notice WHERE user_id=? ORDER BY id DESC LIMIT 200",UserContextHolder.getCurrentUserId()); }
    public void read(long id) { db.update("UPDATE t_platform_notice SET read_at=COALESCE(read_at,NOW()) WHERE id=? AND user_id=?",id,UserContextHolder.getCurrentUserId()); }
    public List<Map<String,Object>> ownContent(String kind) {
        long uid=UserContextHolder.getCurrentUserId();
        String sql=switch(kind) {
            case "reviews" -> "SELECT id,content AS title,status,created_at FROM t_landlord_review WHERE reviewer_id=?";
            case "guides" -> "SELECT id,title,status,created_at FROM t_rental_guide WHERE author_id=?";
            default -> throw new BusinessException("内容类型无效");
        };
        return db.queryForList(sql+" ORDER BY id DESC LIMIT 200",uid);
    }
    @Transactional public void moderate(String kind,long id,Map<String,Object> body) {
        HousingTrustService.admin();
        String note=HousingTrustService.text(body,"note",1000),status=HousingTrustService.text(body,"status",30);
        String table,owner,title;
        if(kind.equals("review")) { table="t_landlord_review";owner="reviewer_id";title="房东评价";if(!Set.of("APPROVED","REJECTED","OFFLINE").contains(status))throw new BusinessException("评价状态无效"); }
        else if(kind.equals("guide")) {table="t_rental_guide";owner="author_id";title="租房攻略";if(!Set.of("published","REJECTED","OFFLINE").contains(status))throw new BusinessException("攻略状态无效");}
        else throw new BusinessException("管理类型无效");
        var rows=db.queryForList("SELECT * FROM "+table+" WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty())throw BusinessException.notFound("记录不存在");var row=rows.get(0);
        if(status.equals(row.get("status")))throw new BusinessException("状态未发生变化，请刷新记录");
        if((status.equals("APPROVED")||status.equals("published"))&&!"PENDING".equals(row.get("status")))throw new BusinessException("只有待审核记录可通过，请由作者重新提交");
        if((status.equals("APPROVED")||status.equals("published"))&&HousingTrustService.number(row.get(owner))==UserContextHolder.getCurrentUserId())throw new BusinessException("不能审核本人提交的内容");
        db.update("UPDATE "+table+" SET status=? WHERE id=?",status,id);
        record(kind,id,status,note);notify(HousingTrustService.number(row.get(owner)),title+"处理结果",status+"："+note,"/app/workflow");
    }
    public Map<String,Object> ownDetail(String kind,long id) {
        String table,owner;
        if(kind.equals("guides")){table="t_rental_guide";owner="author_id";}
        else if(kind.equals("reviews")){table="t_landlord_review";owner="reviewer_id";}
        else throw new BusinessException("内容类型无效");
        var rows=db.queryForList("SELECT * FROM "+table+" WHERE id=? AND "+owner+"=?",id,UserContextHolder.getCurrentUserId());
        if(rows.isEmpty())throw BusinessException.notFound("记录不存在");return rows.get(0);
    }
    @Transactional public void resubmit(String kind,long id,Map<String,Object> body) {
        var row=ownDetail(kind,id);
        if(!Set.of("REJECTED","OFFLINE").contains(Objects.toString(row.get("status"))))throw new BusinessException("仅退回或下架内容可重新提交");
        String content=HousingTrustService.text(body,"content",kind.equals("guides")?30000:2000);
        if(kind.equals("guides"))db.update("UPDATE t_rental_guide SET title=?,content=?,status='PENDING',updated_at=NOW() WHERE id=?",HousingTrustService.text(body,"title",200),content,id);
        else db.update("UPDATE t_landlord_review SET content=?,status='PENDING' WHERE id=?",content,id);
        record(kind.equals("guides")?"guide":"review",id,"PENDING","作者修改后重新提交");
    }
    @Transactional public void userStatus(long id,int status,String note) {
        HousingTrustService.admin();if(status!=0&&status!=1)throw new BusinessException("账号状态无效");
        var users=db.queryForList("SELECT * FROM t_user WHERE id=? FOR UPDATE",id);if(users.isEmpty())throw BusinessException.notFound("用户不存在");
        if("ADMIN".equals(users.get(0).get("role"))||id==UserContextHolder.getCurrentUserId())throw new BusinessException("不可在此停用管理员账号");
        db.update("UPDATE t_user SET status=? WHERE id=?",status,id);
        // Re-enabling an account never silently re-publishes its previous listings.
        if(status==0)db.update("UPDATE t_house SET status='OFFLINE',updated_at=NOW() WHERE landlord_id=? AND status='APPROVED'",id);
        record("user",id,Integer.toString(status),note);notify(id,"账号状态变更",(status==1?"账号已恢复：":"账号已停用，公开房源已下架：")+note,"/app/workflow");
    }
}
