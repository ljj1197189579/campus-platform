package com.campus.platform.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import com.campus.platform.service.WalletService;

@Repository
public class OrderRepository {
    private final JdbcTemplate db;
    private final WalletService wallet;

    public OrderRepository(JdbcTemplate db, WalletService wallet) {
        this.db = db;
        this.wallet = wallet;
    }

    public List<Map<String, Object>> list(long userId) {
        return db.queryForList("""
            select o.id,o.goods_id,o.buyer_id,o.status,o.message,o.created_at,
            g.seller_id,g.title,coalesce(p.amount,g.price) price,p.fee service_fee,p.status payment_status,p.note payment_note,g.delivery_mode,g.delivery_note,g.bargaining_allowed,
            buyer.nickname buyer_nickname,seller.nickname seller_nickname,r.rating,r.content review_content
            from order_intention o join goods g on g.id=o.goods_id
            join sys_user buyer on buyer.id=o.buyer_id join sys_user seller on seller.id=g.seller_id
            left join seller_review r on r.order_id=o.id
            left join service_fee p on p.order_id=o.id
            where o.buyer_id=? or g.seller_id=? order by o.id desc limit 200
            """, userId, userId);
    }

    public Map<String, Object> credit(long userId) {
        return db.queryForMap("""
            select round(avg(r.rating),2) seller_credit,count(o.id) completed_trades,count(r.id) review_count
            from order_intention o join goods g on g.id=o.goods_id
            left join seller_review r on r.order_id=o.id
            where g.seller_id=? and o.status='COMPLETED'
            """, userId);
    }

    private Map<String, Object> lockedOrder(long orderId) {
        try {
            Long goodsId = db.queryForObject("select goods_id from order_intention where id=?", Long.class, orderId);
            db.queryForMap("select id from goods where id=? for update", goodsId);
            return db.queryForMap("""
                select o.id,o.goods_id,o.buyer_id,o.status,g.seller_id,g.price,g.trade_status,g.audit_status
                from order_intention o join goods g on g.id=o.goods_id where o.id=? for update
                """, orderId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
    }

    @Transactional
    public void transition(long userId, long orderId, String action) {
        if (!List.of("ACCEPT", "REJECT", "CANCEL", "COMPLETE").contains(action))
            throw new IllegalArgumentException("订单操作不合法");
        Map<String, Object> order = lockedOrder(orderId);
        boolean buyer = ((Number) order.get("buyer_id")).longValue() == userId;
        boolean seller = ((Number) order.get("seller_id")).longValue() == userId;
        if (!buyer && !seller) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作此订单");
        String state = String.valueOf(order.get("status"));
        Object goodsId = order.get("goods_id");
        switch (action) {
            case "ACCEPT" -> {
                if (!seller) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅卖家可以接受意向");
                requireState("PENDING".equals(state) && "ON_SALE".equals(order.get("trade_status"))
                        && "APPROVED".equals(order.get("audit_status")));
                db.update("update goods set trade_status='RESERVED' where id=?", goodsId);
                db.update("update order_intention set status='ACCEPTED' where id=?", orderId);
                wallet.prepare(orderId, new BigDecimal(order.get("price").toString()));
                db.update("update order_intention set status='REJECTED' where goods_id=? and id<>? and status='PENDING'", goodsId, orderId);
            }
            case "REJECT" -> {
                if (!seller) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅卖家可以拒绝意向");
                requireState("PENDING".equals(state));
                db.update("update order_intention set status='REJECTED' where id=?", orderId);
            }
            case "CANCEL" -> {
                requireState("PENDING".equals(state) || "ACCEPTED".equals(state));
                wallet.refund(((Number) order.get("buyer_id")).longValue(), orderId);
                db.update("update order_intention set status='CANCELLED' where id=?", orderId);
                if ("ACCEPTED".equals(state)) {
                    requireState("RESERVED".equals(order.get("trade_status")));
                    db.update("update goods set trade_status='ON_SALE' where id=?", goodsId);
                }
            }
            case "COMPLETE" -> {
                if (!buyer) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅买家可以确认收货");
                requireState("ACCEPTED".equals(state) && "RESERVED".equals(order.get("trade_status")));
                wallet.settle(((Number) order.get("seller_id")).longValue(), orderId);
                db.update("update order_intention set status='COMPLETED' where id=?", orderId);
                db.update("update goods set trade_status='SOLD' where id=?", goodsId);
            }
        }
    }

    @Transactional
    public void review(long userId, long orderId, int rating, String content) {
        if (rating < 1 || rating > 5 || (content != null && content.length() > 255))
            throw new IllegalArgumentException("评价参数不合法");
        Map<String, Object> order = lockedOrder(orderId);
        if (((Number) order.get("buyer_id")).longValue() != userId)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅买家可以评价");
        requireState("COMPLETED".equals(order.get("status")));
        try {
            db.update("insert into seller_review(order_id,rating,content) values(?,?,?)", orderId, rating, content == null ? "" : content);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "此订单已评价");
        }
    }

    private void requireState(boolean allowed) {
        if (!allowed) throw new ResponseStatusException(HttpStatus.CONFLICT, "订单状态已变化，请刷新后重试");
    }
}
