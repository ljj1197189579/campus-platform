package com.campus.platform.repository;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@Repository
public class ChatRepository {
    private final JdbcTemplate db;

    public ChatRepository(JdbcTemplate db) { this.db = db; }

    public List<Map<String, Object>> conversations(long userId) {
        return db.queryForList("""
            select c.id,u.id peer_id,u.nickname peer_name,u.status peer_status,c.updated_at,
            (select content from chat_message where conversation_id=c.id order by id desc limit 1) last_message,
            (select count(*) from chat_message where conversation_id=c.id and sender_id<>?
                and id>case when c.user_low=? then c.low_read_id else c.high_read_id end) unread_count
            from chat_conversation c join sys_user u on u.id=case when c.user_low=? then c.user_high else c.user_low end
            where c.user_low=? or c.user_high=? order by c.updated_at desc,c.id desc
            """, userId, userId, userId, userId, userId);
    }

    @Transactional
    public long start(long userId, long goodsId) {
        List<Map<String, Object>> goods = db.queryForList("select g.seller_id from goods g join sys_user u on u.id=g.seller_id where g.id=? and g.audit_status='APPROVED' and g.trade_status='ON_SALE' and u.status='NORMAL'", goodsId);
        if (goods.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品暂不可联系");
        long sellerId = ((Number) goods.get(0).get("seller_id")).longValue();
        if (sellerId == userId) throw new IllegalArgumentException("这是你发布的商品");
        long low = Math.min(userId, sellerId);
        long high = Math.max(userId, sellerId);
        db.update("insert into chat_conversation(user_low,user_high) values(?,?) on duplicate key update id=id", low, high);
        return db.queryForObject("select id from chat_conversation where user_low=? and user_high=?", Long.class, low, high);
    }

    private Map<String, Object> member(long userId, long conversationId, boolean lock) {
        List<Map<String, Object>> rows = db.queryForList("select user_low,user_high from chat_conversation where id=? and (user_low=? or user_high=?)" + (lock ? " for update" : ""), conversationId, userId, userId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "对话不存在或不可访问");
        return rows.get(0);
    }

    public List<Map<String, Object>> messages(long userId, long conversationId, Long before) {
        member(userId, conversationId, false);
        return db.queryForList("select id,sender_id,content,created_at from chat_message where conversation_id=? and id<? order by id desc limit 50", conversationId, before == null ? Long.MAX_VALUE : before);
    }

    @Transactional
    public void send(long userId, long conversationId, String content) {
        Map<String, Object> conversation = member(userId, conversationId, true);
        long peerId = ((Number) conversation.get("user_low")).longValue() == userId ? ((Number) conversation.get("user_high")).longValue() : ((Number) conversation.get("user_low")).longValue();
        if (db.queryForObject("select count(*) from sys_user where id=? and status='NORMAL'", Integer.class, peerId) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "对方账号暂不可接收消息");
        db.update("insert into chat_message(conversation_id,sender_id,content) values(?,?,?)", conversationId, userId, content.trim());
        db.update("update chat_conversation set updated_at=now() where id=?", conversationId);
    }

    @Transactional
    public void read(long userId, long conversationId, long messageId) {
        Map<String, Object> conversation = member(userId, conversationId, true);
        if (db.queryForObject("select count(*) from chat_message where conversation_id=? and id=?", Integer.class, conversationId, messageId) != 1)
            throw new IllegalArgumentException("消息不存在");
        String column = ((Number) conversation.get("user_low")).longValue() == userId ? "low_read_id" : "high_read_id";
        db.update("update chat_conversation set " + column + "=greatest(" + column + ",?) where id=?", messageId, conversationId);
    }
}
