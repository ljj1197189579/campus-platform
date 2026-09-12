package com.campus.platform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class WalletService {
    private final JdbcTemplate db;
    private final BigDecimal feeRate;

    public WalletService(JdbcTemplate db, @Value("${campus.service-fee-rate:0.02}") BigDecimal feeRate) {
        if (feeRate.compareTo(BigDecimal.ZERO) < 0 || feeRate.compareTo(BigDecimal.ONE) > 0)
            throw new IllegalArgumentException("服务费比例需在0到1之间");
        this.db = db;
        this.feeRate = feeRate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void prepare(long orderId, BigDecimal amount) {
        BigDecimal fee = amount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
        db.update("insert into service_fee(order_id,amount,fee,status) values(?,?,?,?)", orderId, amount, fee, fee.signum() == 0 ? "CONFIRMED" : "UNPAID");
    }

    private Map<String, Object> fee(long orderId) {
        List<Map<String, Object>> rows = db.queryForList("select order_id,amount,fee,status from service_fee where order_id=? for update", orderId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "此订单需要重新提交购买申请");
        return rows.get(0);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void settle(long sellerId, long orderId) {
        if (!"CONFIRMED".equals(fee(orderId).get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务费尚未核实到账，请等待核实后再确认收货");
        db.update("update service_fee set status='SETTLED' where order_id=?", orderId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void refund(long buyerId, long orderId) {
        db.update("update service_fee set status=case when status in ('CONFIRMED','PENDING') and fee>0 then 'REFUND_PENDING' else 'CANCELLED' end where order_id=? and status in ('UNPAID','PENDING','CONFIRMED','REJECTED')", orderId);
    }

    private Map<String, Object> lockOrder(long orderId) {
        try {
            Long goodsId = db.queryForObject("select goods_id from order_intention where id=?", Long.class, orderId);
            db.queryForMap("select id from goods where id=? for update", goodsId);
            return db.queryForMap("select buyer_id,status from order_intention where id=? for update", orderId);
        } catch (EmptyResultDataAccessException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "交易不存在");
        }
    }

    public Map<String, Object> settings() {
        Map<String, Object> settings = db.queryForMap("select qr_url,payee_name,instructions from payment_setting where id=1");
        settings.put("serviceFeeRate", feeRate);
        return settings;
    }

    public void configure(long adminId, String qrUrl, String payee, String instructions) {
        if (qrUrl == null || !qrUrl.matches("/api/media/[0-9a-f-]{36}") || payee == null || payee.isBlank() || payee.length() > 80 || instructions.length() > 255)
            throw new IllegalArgumentException("请上传收款码并填写收款人信息");
        Integer owned = db.queryForObject("select count(*) from media_asset where id=? and owner_id=?", Integer.class, qrUrl.substring(11), adminId);
        if (owned == null || owned != 1) throw new IllegalArgumentException("请使用自己上传的收款码");
        db.update("update payment_setting set qr_url=?,payee_name=?,instructions=? where id=1", qrUrl, payee.trim(), instructions);
    }

    @Transactional
    public void submitProof(long buyerId, long orderId, String reference, byte[] proof) {
        Map<String, Object> order = lockOrder(orderId);
        if (((Number) order.get("buyer_id")).longValue() != buyerId) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅买家可以提交付款凭证");
        if (!"ACCEPTED".equals(order.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "当前交易无法提交付款凭证");
        Map<String, Object> payment = fee(orderId);
        if (!List.of("UNPAID", "REJECTED").contains(payment.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "已提交凭证，请勿重复提交");
        if (settings().get("qr_url") == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "收款方式尚未设置，请稍后再试");
        if (reference == null || reference.isBlank() || reference.length() > 100 || proof.length == 0) throw new IllegalArgumentException("请填写付款单号并上传凭证");
        Integer duplicate = db.queryForObject("select count(*) from service_fee where payer_reference=? and order_id<>? and status not in ('REJECTED','CANCELLED')", Integer.class, reference.trim(), orderId);
        if (duplicate != null && duplicate > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "此付款记录已用于其他交易，请核对");
        db.update("update service_fee set payer_reference=?,proof=?,status='PENDING',note=null where order_id=?", reference.trim(), proof, orderId);
    }

    public byte[] proof(long userId, boolean admin, long orderId) {
        List<Map<String, Object>> rows = db.queryForList("select s.proof from service_fee s join order_intention o on o.id=s.order_id where s.order_id=? and (? or o.buyer_id=?)", orderId, admin, userId);
        if (rows.isEmpty() || rows.get(0).get("proof") == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "凭证不存在或不可查看");
        return (byte[]) rows.get(0).get("proof");
    }

    @Transactional
    public void verify(long adminId, long orderId, String action, String reference, BigDecimal actualAmount, String note) {
        Map<String, Object> order = lockOrder(orderId);
        Map<String, Object> payment = fee(orderId);
        String state = String.valueOf(payment.get("status"));
        if (!List.of("CONFIRM", "REJECT", "REFUND", "NO_PAYMENT").contains(action)) throw new IllegalArgumentException("请选择有效的处理方式");
        if ("CONFIRM".equals(action)) {
            if (!"PENDING".equals(state) || !"ACCEPTED".equals(order.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "该记录已处理或交易已取消");
            checkReference(reference, actualAmount, payment);
            Integer used = db.queryForObject("select count(*) from service_fee where verified_reference=? and order_id<>?", Integer.class, reference.trim(), orderId);
            if (used != null && used > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "此到账单号已使用，不能重复确认");
            db.update("update service_fee set status='CONFIRMED',verified_reference=?,verified_by=?,verified_at=now(),note=? where order_id=?", reference.trim(), adminId, note, orderId);
        } else if ("REJECT".equals(action)) {
            if (!"PENDING".equals(state)) throw new ResponseStatusException(HttpStatus.CONFLICT, "该记录已处理");
            if (note == null || note.isBlank()) throw new IllegalArgumentException("请填写未通过的原因");
            db.update("update service_fee set status='REJECTED',verified_by=?,note=? where order_id=?", adminId, note, orderId);
        } else {
            if (!"REFUND_PENDING".equals(state)) throw new ResponseStatusException(HttpStatus.CONFLICT, "当前没有待处理退款");
            if ("REFUND".equals(action)) {
                checkReference(reference, actualAmount, payment);
                db.update("update service_fee set status='REFUNDED',refund_reference=?,verified_by=?,note=? where order_id=?", reference.trim(), adminId, note, orderId);
            } else {
                Integer verified = db.queryForObject("select count(*) from service_fee where order_id=? and verified_reference is not null", Integer.class, orderId);
                if (verified != null && verified > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "已确认到账的款项必须退款，不能标记未收到");
                if (note == null || note.isBlank()) throw new IllegalArgumentException("请填写核实说明");
                db.update("update service_fee set status='CANCELLED',verified_by=?,note=? where order_id=?", adminId, note, orderId);
            }
        }
    }

    private void checkReference(String reference, BigDecimal actualAmount, Map<String, Object> payment) {
        if (reference == null || reference.isBlank() || reference.length() > 100 || actualAmount == null || actualAmount.scale() > 2 ||
                new BigDecimal(payment.get("fee").toString()).compareTo(actualAmount) != 0)
            throw new IllegalArgumentException("请核对实际交易单号，实际金额须与本单服务费一致");
    }

    public List<Map<String, Object>> pending() {
        return db.queryForList("select s.order_id,s.amount,s.fee,s.status,s.payer_reference,s.note,s.verified_reference,u.nickname buyer_name,g.title from service_fee s join order_intention o on o.id=s.order_id join sys_user u on u.id=o.buyer_id join goods g on g.id=o.goods_id where s.status in ('PENDING','REFUND_PENDING') order by s.created_at");
    }

    public Map<String, Object> overview(long userId) {
        return Map.of("serviceFeeRate", feeRate,
                "paidFees", db.queryForObject("select coalesce(sum(s.fee),0) from service_fee s join order_intention o on o.id=s.order_id where o.buyer_id=? and s.status in ('CONFIRMED','SETTLED')", BigDecimal.class, userId),
                "pendingFees", db.queryForObject("select coalesce(sum(s.fee),0) from service_fee s join order_intention o on o.id=s.order_id where o.buyer_id=? and s.status='PENDING'", BigDecimal.class, userId),
                "pendingRefunds", db.queryForObject("select coalesce(sum(s.fee),0) from service_fee s join order_intention o on o.id=s.order_id where o.buyer_id=? and s.status='REFUND_PENDING'", BigDecimal.class, userId),
                "payments", db.queryForList("select s.order_id,s.fee,s.status,s.note,s.created_at,g.title from service_fee s join order_intention o on o.id=s.order_id join goods g on g.id=o.goods_id where o.buyer_id=? order by s.created_at desc limit 100", userId));
    }

    public Map<String, Object> revenue() {
        return Map.of("serviceFeeRate", feeRate,
                "settledFees", db.queryForObject("select coalesce(sum(fee),0) from service_fee where status='SETTLED'", BigDecimal.class),
                "pendingFees", db.queryForObject("select coalesce(sum(fee),0) from service_fee where status='CONFIRMED'", BigDecimal.class),
                "pendingRefunds", db.queryForObject("select coalesce(sum(fee),0) from service_fee where status='REFUND_PENDING'", BigDecimal.class));
    }
}
