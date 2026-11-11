package com.rental.platform.service;

import com.rental.platform.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;
import java.util.*;

@Service
public class PrivateMessageService {
    private final JdbcTemplate db;
    public PrivateMessageService(JdbcTemplate db) { this.db=db; }
    @PostConstruct public void initialize() {
        db.execute("""
          CREATE TABLE IF NOT EXISTS t_private_conversation (
           id BIGINT PRIMARY KEY AUTO_INCREMENT, house_id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, landlord_id BIGINT NOT NULL,
           created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
           UNIQUE KEY uq_private_house_tenant(house_id,tenant_id), KEY idx_private_tenant(tenant_id,updated_at), KEY idx_private_landlord(landlord_id,updated_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
          """);
        db.execute("""
          CREATE TABLE IF NOT EXISTS t_private_message (
           id BIGINT PRIMARY KEY AUTO_INCREMENT, conversation_id BIGINT NOT NULL, sender_id BIGINT NOT NULL, content TEXT NOT NULL,
           source VARCHAR(12) NOT NULL DEFAULT 'USER', request_id VARCHAR(80) NOT NULL, read_at DATETIME(6),
           created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), UNIQUE KEY uq_private_request(sender_id,request_id),
           KEY idx_private_history(conversation_id,id), KEY idx_private_unread(conversation_id,read_at,sender_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
          """);
        db.execute("""
          CREATE TABLE IF NOT EXISTS t_private_draft (
           id VARCHAR(36) PRIMARY KEY, owner_id BIGINT NOT NULL, conversation_id BIGINT NOT NULL, content TEXT NOT NULL,
           expires_at DATETIME(6) NOT NULL, sent_message_id BIGINT, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
           KEY idx_private_draft_owner(owner_id,expires_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
          """);
    }
    public static long number(Object value) { return ((Number)value).longValue(); }
    public Map<String,Object> conversation(long id,long userId,boolean lock) {
        var rows=db.queryForList("SELECT * FROM t_private_conversation WHERE id=? AND (tenant_id=? OR landlord_id=?)"+(lock?" FOR UPDATE":""),id,userId,userId);
        if(rows.isEmpty())throw BusinessException.forbidden("无权访问这段私信");return rows.get(0);
    }
    @Transactional public long start(long houseId,long userId) {
        var rows=db.queryForList("SELECT h.landlord_id,h.status,u.status AS owner_status FROM t_house h JOIN t_user u ON u.id=h.landlord_id WHERE h.id=?",houseId);
        if(rows.isEmpty())throw BusinessException.notFound("房源不存在或发布者不可用");var house=rows.get(0);long owner=number(house.get("landlord_id"));
        if(owner==userId)throw new BusinessException("这是你发布的房源，请从我的消息回复租客");
        if(!"APPROVED".equals(house.get("status")))throw new BusinessException("该房源尚未通过审核或已下架");
        if(number(house.get("owner_status"))!=1)throw new BusinessException("房源发布者当前不可联系");
        db.update("INSERT INTO t_private_conversation(house_id,tenant_id,landlord_id) VALUES(?,?,?) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)",houseId,userId,owner);
        return db.queryForObject("SELECT id FROM t_private_conversation WHERE house_id=? AND tenant_id=?",Long.class,houseId,userId);
    }
    public List<Map<String,Object>> inbox(long userId) {
        return db.queryForList("""
          SELECT c.id,c.house_id AS houseId,h.title AS houseTitle,h.city,h.cover_image AS coverImage,
           IF(c.tenant_id=?,c.landlord_id,c.tenant_id) AS otherId,COALESCE(NULLIF(u.real_name,''),u.username) AS otherName,u.role AS otherRole,
           (SELECT content FROM t_private_message WHERE conversation_id=c.id ORDER BY id DESC LIMIT 1) AS lastMessage,
           (SELECT COUNT(*) FROM t_private_message WHERE conversation_id=c.id AND sender_id<>? AND read_at IS NULL) AS unread,c.updated_at AS updatedAt
          FROM t_private_conversation c JOIN t_house h ON h.id=c.house_id
          JOIN t_user u ON u.id=IF(c.tenant_id=?,c.landlord_id,c.tenant_id)
          WHERE (c.tenant_id=? OR c.landlord_id=?) AND (c.tenant_id=? OR EXISTS(SELECT 1 FROM t_private_message WHERE conversation_id=c.id)) ORDER BY c.updated_at DESC,c.id DESC LIMIT 200
          """,userId,userId,userId,userId,userId,userId);
    }
    public int unread(long userId) {
        return db.queryForObject("SELECT COUNT(*) FROM t_private_message m JOIN t_private_conversation c ON c.id=m.conversation_id WHERE (c.tenant_id=? OR c.landlord_id=?) AND m.sender_id<>? AND m.read_at IS NULL",Integer.class,userId,userId,userId);
    }
    public List<Map<String,Object>> history(long id,long userId,long after,long before) {
        conversation(id,userId,false);
        var rows=db.queryForList("SELECT id,sender_id AS senderId,content,source,created_at AS createdAt,read_at AS readAt FROM t_private_message WHERE conversation_id=? AND id>? AND (?=0 OR id<?) ORDER BY id "+(after>0?"ASC":"DESC")+" LIMIT 100",id,Math.max(0,after),before,before);
        if(after==0)Collections.reverse(rows);return rows;
    }
    public void read(long id,long userId,long through) {conversation(id,userId,false);db.update("UPDATE t_private_message SET read_at=CURRENT_TIMESTAMP(6) WHERE conversation_id=? AND sender_id<>? AND read_at IS NULL AND id<=?",id,userId,through);}
    public static String text(String value) {if(value==null||value.isBlank())throw new BusinessException("消息不能为空");String clean=value.trim();if(clean.length()>2000)throw new BusinessException("消息不能超过2000字");return clean;}
    @Transactional public Map<String,Object> send(long id,long userId,String content,String requestId,String source) {
        var c=conversation(id,userId,true);String message=text(content);
        if(requestId==null||!requestId.matches("[A-Za-z0-9_-]{12,80}"))throw new BusinessException("消息标识无效，请刷新重试");
        var old=db.queryForList("SELECT id,conversation_id AS conversationId FROM t_private_message WHERE sender_id=? AND request_id=?",userId,requestId);
        if(!old.isEmpty()){if(number(old.get(0).get("conversationId"))!=id)throw new BusinessException("消息标识已用于其他会话");return old.get(0);}
        long other=number(c.get("tenant_id"))==userId?number(c.get("landlord_id")):number(c.get("tenant_id"));
        if(db.queryForObject("SELECT status FROM t_user WHERE id=?",Integer.class,other)!=1)throw new BusinessException("对方账号当前不可接收消息");
        int recent=db.queryForObject("SELECT COUNT(*) FROM t_private_message WHERE sender_id=? AND created_at>DATE_SUB(NOW(),INTERVAL 1 MINUTE)",Integer.class,userId);
        if(recent>=20)throw new BusinessException("发送过于频繁，请稍后再试");
        db.update("INSERT INTO t_private_message(conversation_id,sender_id,content,source,request_id) VALUES(?,?,?,?,?)",id,userId,message,source,requestId);
        db.update("UPDATE t_private_conversation SET updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",id);
        return db.queryForMap("SELECT id,conversation_id AS conversationId FROM t_private_message WHERE sender_id=? AND request_id=?",userId,requestId);
    }
    @Transactional public Map<String,Object> prepare(long houseId,long userId,String content) {return prepareForConversation(start(houseId,userId),userId,content);}
    public Map<String,Object> prepareForConversation(long id,long userId,String content) {
        conversation(id,userId,false);String draftId=UUID.randomUUID().toString();
        db.update("INSERT INTO t_private_draft(id,owner_id,conversation_id,content,expires_at) VALUES(?,?,?,?,DATE_ADD(NOW(),INTERVAL 15 MINUTE))",draftId,userId,id,text(content));
        return pending(userId).stream().filter(d->draftId.equals(d.get("id"))).findFirst().orElseThrow();
    }
    public List<Map<String,Object>> pending(long userId) {
        return db.queryForList("""
          SELECT d.id,d.content,d.conversation_id AS conversationId,h.id AS houseId,h.title AS houseTitle,
           COALESCE(NULLIF(u.real_name,''),u.username) AS recipientName
          FROM t_private_draft d JOIN t_private_conversation c ON c.id=d.conversation_id JOIN t_house h ON h.id=c.house_id
          JOIN t_user u ON u.id=IF(c.tenant_id=?,c.landlord_id,c.tenant_id)
          WHERE d.owner_id=? AND d.sent_message_id IS NULL AND d.expires_at>NOW() ORDER BY d.created_at DESC LIMIT 20
          """,userId,userId);
    }
    public void cancel(String id,long userId) {db.update("UPDATE t_private_draft SET expires_at=NOW() WHERE id=? AND owner_id=? AND sent_message_id IS NULL",id,userId);}
    @Transactional public Map<String,Object> confirm(String draftId,long userId,String edited) {
        var rows=db.queryForList("SELECT *,IF(expires_at>NOW(),1,0) AS valid FROM t_private_draft WHERE id=? AND owner_id=? FOR UPDATE",draftId,userId);
        if(rows.isEmpty())throw BusinessException.notFound("草稿不存在");var draft=rows.get(0);
        if(draft.get("sent_message_id")!=null)return Map.of("id",draft.get("sent_message_id"),"conversationId",draft.get("conversation_id"));
        if(number(draft.get("valid"))!=1)throw new BusinessException("草稿已过期，请重新生成");
        var result=send(number(draft.get("conversation_id")),userId,edited==null?(String)draft.get("content"):edited,"ai_"+draftId,"AI");
        db.update("UPDATE t_private_draft SET sent_message_id=? WHERE id=?",result.get("id"),draftId);return result;
    }
}
