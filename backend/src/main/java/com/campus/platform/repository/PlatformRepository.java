package com.campus.platform.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@Repository
public class PlatformRepository {
    private final JdbcTemplate db;

    public PlatformRepository(JdbcTemplate db) {
        this.db = db;
    }

    public Map<String, Object> user(String username) {
        try {
            return db.queryForMap("select id,username,nickname,role,password_hash from sys_user where username=? and status='NORMAL'", username);
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
    }

    public void user(String username, String password, String nickname) {
        try {
            db.update("insert into sys_user(username,password_hash,nickname) values(?,?,?)", username, password, nickname);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }

    public void upgradePassword(long userId, String previous, String encoded) {
        db.update("update sys_user set password_hash=? where id=? and password_hash=?", encoded, userId, previous);
    }

    public Map<String, Object> activeUser(long userId) {
        return db.queryForMap("select id,username,role from sys_user where id=? and status='NORMAL'", userId);
    }

    public List<Map<String, Object>> categories() {
        return db.queryForList("select id,name from goods_category order by sort_no,id");
    }

    public List<Map<String, Object>> mine(long userId, boolean favorites) {
        String selection = favorites ? "g.id in (select goods_id from favorite where user_id=?) and g.audit_status='APPROVED' and u.status='NORMAL'" : "g.seller_id=?";
        return withImages(db.queryForList("select g.*,c.name category,u.nickname seller_nickname from goods g join goods_category c on c.id=g.category_id join sys_user u on u.id=g.seller_id where " + selection + " order by g.id desc", userId));
    }

    public void favorite(long userId, long goodsId, boolean save) {
        if (!save) { db.update("delete from favorite where user_id=? and goods_id=?", userId, goodsId); return; }
        if (db.queryForObject("select count(*) from goods g join sys_user u on u.id=g.seller_id where g.id=? and g.audit_status='APPROVED' and g.trade_status='ON_SALE' and u.status='NORMAL'", Integer.class, goodsId) != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品暂不可收藏");
        db.update("insert into favorite(user_id,goods_id) values(?,?) on duplicate key update id=id", userId, goodsId);
    }

    @Transactional
    public Map<String, Object> view(long goodsId) {
        if (db.update("update goods g join sys_user u on u.id=g.seller_id set g.view_count=g.view_count+1 where g.id=? and g.audit_status='APPROVED' and g.trade_status='ON_SALE' and u.status='NORMAL'", goodsId) != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品已下架或暂不可查看");
        return db.queryForMap("select view_count from goods where id=?", goodsId);
    }

    public List<Map<String, Object>> goods() {
        return withImages(db.queryForList("""
            select g.id,g.title,g.description,g.image_url,g.seller_id,c.name category,g.price,
            g.original_price,g.trade_location,g.view_count,g.condition_level,g.delivery_mode,g.delivery_note,g.bargaining_allowed,
            g.audit_status,g.trade_status,u.nickname seller_nickname,
            reputation.seller_credit,coalesce(reputation.completed_trades,0) completed_trades
            from goods g join goods_category c on c.id=g.category_id
            join sys_user u on u.id=g.seller_id
            left join (
                select sold.seller_id,round(avg(r.rating),2) seller_credit,count(o.id) completed_trades
                from order_intention o join goods sold on sold.id=o.goods_id
                left join seller_review r on r.order_id=o.id where o.status='COMPLETED'
                group by sold.seller_id
            ) reputation on reputation.seller_id=g.seller_id
            where g.audit_status='APPROVED' and g.trade_status='ON_SALE' and u.status='NORMAL'
            order by g.id desc limit 200
            """));
    }

    @Transactional
    public long addGoods(long userId, Map<String, Object> values, List<String> images) {
        if (images == null || images.isEmpty() || images.size() > 6 || images.stream().distinct().count() != images.size())
            throw new IllegalArgumentException("请上传1至6张不同的商品实拍图");
        for (String image : images) {
            if (image == null || !image.matches("/api/media/[0-9a-f-]{36}")) throw new IllegalArgumentException("请重新上传商品图片");
            Integer owned = db.queryForObject("select count(*) from media_asset where id=? and owner_id=?", Integer.class, image.substring(11), userId);
            if (owned == null || owned != 1) throw new IllegalArgumentException("只能使用自己上传的图片");
        }
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        db.update(connection -> {
            var statement = connection.prepareStatement("""
                insert into goods(seller_id,category_id,title,description,price,condition_level,
                delivery_mode,delivery_note,bargaining_allowed,original_price,trade_location,audit_status,trade_status)
                values(?,?,?,?,?,?,?,?,?,?,?,'PENDING','ON_SALE')
                """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setObject(2, values.get("categoryId"));
            statement.setObject(3, values.get("title"));
            statement.setObject(4, values.get("description"));
            statement.setObject(5, values.get("price"));
            statement.setObject(6, values.get("condition"));
            statement.setObject(7, values.get("deliveryMode"));
            statement.setObject(8, values.get("deliveryNote"));
            statement.setObject(9, values.get("bargainingAllowed"));
            statement.setObject(10, values.get("originalPrice"));
            statement.setObject(11, values.getOrDefault("tradeLocation", ""));
            return statement;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("商品编号生成失败");
        long id = keys.getKey().longValue();
        for (int index = 0; index < images.size(); index++)
            db.update("insert into goods_image(goods_id,media_id,sort_no) values(?,?,?)", id, images.get(index).substring(11), index);
        db.update("update goods set image_url=? where id=?", images.get(0), id);
        return id;
    }

    public List<Map<String, Object>> lost(String keyword) {
        if (keyword.length() > 100) throw new IllegalArgumentException("搜索内容请勿超过100字");
        return db.queryForList("select id,type,title,description,location,status from lost_found where audit_status='APPROVED' and (?='' or locate(?,concat_ws(' ',title,description,location))>0) order by id desc limit 200", keyword.trim(), keyword.trim());
    }

    public void addLost(long userId, Map<String, Object> values) {
        db.update("insert into lost_found(publisher_id,type,title,description,location,audit_status,status) values(?,?,?,?,?,'PENDING','OPEN')",
                userId, values.get("type"), values.get("title"), values.get("description"), values.get("location"));
    }

    @Transactional
    public void intention(long userId, Map<String, Object> values) {
        Map<String, Object> item;
        try {
            item = db.queryForMap("select seller_id,audit_status,trade_status from goods where id=? for update", values.get("goodsId"));
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        if (((Number) item.get("seller_id")).longValue() == userId) throw new IllegalArgumentException("不能购买自己的商品");
        if (!"APPROVED".equals(item.get("audit_status")) || !"ON_SALE".equals(item.get("trade_status")))
            throw new IllegalArgumentException("商品当前不可交易");
        Integer count = db.queryForObject("select count(*) from order_intention where buyer_id=? and goods_id=? and status in ('PENDING','ACCEPTED')",
                Integer.class, userId, values.get("goodsId"));
        if (count != null && count > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "已提交购买意向");
        db.update("insert into order_intention(buyer_id,goods_id,message) values(?,?,?)", userId, values.get("goodsId"), values.get("message"));
    }

    public List<Map<String, Object>> audits() {
        return withImages(db.queryForList("select id,'GOODS' type,title,description,audit_status from goods where audit_status='PENDING' union all select id,'LOST_FOUND',title,description,audit_status from lost_found where audit_status='PENDING'"));
    }

    private List<Map<String, Object>> withImages(List<Map<String, Object>> items) {
        if (items.isEmpty()) return items;
        List<Long> ids = items.stream().filter(item -> !"LOST_FOUND".equals(item.get("type")))
                .map(item -> ((Number) item.get("id")).longValue()).toList();
        Map<Long, List<String>> grouped = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
            db.queryForList("select goods_id,media_id from goods_image where goods_id in (" + placeholders + ") order by sort_no", ids.toArray())
                    .forEach(image -> grouped.computeIfAbsent(((Number) image.get("goods_id")).longValue(), ignored -> new java.util.ArrayList<>()).add("/api/media/" + image.get("media_id")));
        }
        items.forEach(item -> item.put("images", "LOST_FOUND".equals(item.get("type")) ? List.of() : grouped.getOrDefault(((Number) item.get("id")).longValue(), List.of())));
        return items;
    }

    public void audit(String type, long id, String status) {
        String table = switch (type) {
            case "GOODS" -> "goods";
            case "LOST_FOUND" -> "lost_found";
            default -> throw new IllegalArgumentException("审核类型不合法");
        };
        if (!List.of("APPROVED", "REJECTED").contains(status)) throw new IllegalArgumentException("审核状态不合法");
        if (db.update("update " + table + " set audit_status=? where id=? and audit_status='PENDING'", status, id) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "记录不存在或已审核");
    }
}
