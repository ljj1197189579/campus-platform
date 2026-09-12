package com.campus.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Map;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "CAMPUS_INTEGRATION_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:33317/campus_platform.*")
class MySqlFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUS_INTEGRATION_URL"));
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> System.getenv("CAMPUS_INTEGRATION_PASSWORD"));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String login(String username, String password) throws Exception {
        return body(mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn()).get("token").asText();
    }

    private String upload(String token) throws Exception {
        var bitmap = new java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var stream = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(bitmap, "PNG", stream);
        return body(mvc.perform(multipart("/api/media").file(new MockMultipartFile("file", "photo.png", "image/png", stream.toByteArray()))
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn()).get("url").asText();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder proof(long orderId, String reference) throws Exception {
        var bitmap = new java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var stream = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(bitmap, "PNG", stream);
        return multipart("/api/orders/" + orderId + "/payment-proof")
                .file(new MockMultipartFile("file", "receipt.png", "image/png", stream.toByteArray())).param("reference", reference);
    }

    @Test void registrationPublicationReviewAndCreditUseRealMySql() throws Exception {
        String suffix = Long.toString(System.currentTimeMillis());
        String buyerName = "buyer_" + suffix;
        String sellerName = "seller_" + suffix;
        for (String name : new String[]{buyerName, sellerName}) {
            mvc.perform(post("/api/auth/register").contentType("application/json")
                    .content(json.writeValueAsString(Map.of("username", name, "password", "test-password-123"))))
                    .andExpect(status().isOk());
        }
        String buyer = login(buyerName, "test-password-123");
        String seller = login(sellerName, "test-password-123");
        String admin = login("admin", "admin123");
        String qr = upload(admin);
        mvc.perform(put("/api/admin/payment-settings").header("Authorization", "Bearer " + buyer).contentType("application/json")
                .content(json.writeValueAsString(Map.of("qrUrl", qr, "payeeName", "Test platform", "instructions", "Fee only"))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/payment-settings").header("Authorization", "Bearer " + admin).contentType("application/json")
                .content(json.writeValueAsString(Map.of("qrUrl", qr, "payeeName", "Test platform", "instructions", "Fee only"))))
                .andExpect(status().isOk());
        String photo = upload(seller);
        String secondPhoto = upload(seller);
        mvc.perform(get(photo)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(multipart("/api/media").file(new MockMultipartFile("file", "bad.png", "image/png", "not a photo".getBytes()))
                .header("Authorization", "Bearer " + seller)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/media").file(new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1})))
                .andExpect(status().isUnauthorized());
        assertTrue(db.queryForObject("select password_hash from sys_user where username=?", String.class, buyerName).startsWith("pbkdf2$"));
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", buyerName, "password", "test-password-123"))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/audits").header("Authorization", "Bearer " + buyer)).andExpect(status().isForbidden());

        String lostTitle = "Found keys " + suffix;
        mvc.perform(post("/api/lost-found").header("Authorization", "Bearer " + seller).contentType("application/json")
                .content(json.writeValueAsString(Map.of("type", "FOUND", "title", lostTitle, "description", "Two keys", "location", "Library"))))
                .andExpect(status().isOk());
        long lostId = db.queryForObject("select id from lost_found where title=?", Long.class, lostTitle);
        assertEquals("PENDING", db.queryForObject("select audit_status from lost_found where id=?", String.class, lostId));
        mvc.perform(put("/api/admin/audits/LOST_FOUND/" + lostId).param("status", "APPROVED").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/lost-found")).andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value(lostTitle));
        mvc.perform(get("/api/lost-found").param("keyword", "Library")).andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value(lostTitle));
        mvc.perform(get("/api/lost-found").param("keyword", "NoMatchingLostItem")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        String goodsBody = json.writeValueAsString(Map.ofEntries(
                Map.entry("categoryId", 1), Map.entry("title", "Delivery book " + suffix),
                Map.entry("description", "Integration test book"), Map.entry("price", 12.50),
                Map.entry("originalPrice", 25.00), Map.entry("tradeLocation", "Building 3"), Map.entry("condition", "New"),
                Map.entry("deliveryMode", "DORM_DELIVERY"), Map.entry("deliveryNote", "Building 3"),
                Map.entry("bargainingAllowed", false), Map.entry("images", java.util.List.of(photo, secondPhoto))));
        mvc.perform(post("/api/goods").header("Authorization", "Bearer " + buyer)
                .contentType("application/json").content(goodsBody)).andExpect(status().isBadRequest());
        long goodsId = body(mvc.perform(post("/api/goods").header("Authorization", "Bearer " + seller)
                .contentType("application/json").content(goodsBody)).andExpect(status().isOk()).andReturn()).get("id").asLong();
        assertEquals("PENDING", db.queryForObject("select audit_status from goods where id=?", String.class, goodsId));
        mvc.perform(post("/api/goods").header("Authorization", "Bearer " + seller).contentType("application/json")
                .content(goodsBody.replace("DORM_DELIVERY", "INVALID"))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/audits/GOODS/" + goodsId).param("status", "APPROVED").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(put("/api/admin/audits/GOODS/" + goodsId).param("status", "APPROVED").header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());
        JsonNode listed = body(mvc.perform(get("/api/goods")).andExpect(status().isOk()).andReturn());
        JsonNode item = null;
        for (JsonNode candidate : listed) if (candidate.get("id").asLong() == goodsId) item = candidate;
        assertNotNull(item);
        assertEquals("DORM_DELIVERY", item.get("delivery_mode").asText());
        assertFalse(item.get("bargaining_allowed").asBoolean());
        assertEquals(25.0, item.get("original_price").asDouble());
        assertEquals("Building 3", item.get("trade_location").asText());
        assertEquals(0, item.get("view_count").asInt());
        assertTrue(item.get("seller_credit").isNull());
        assertEquals(2, item.get("images").size());
        mvc.perform(post("/api/goods/" + goodsId + "/views")).andExpect(status().isOk()).andExpect(jsonPath("$.view_count").value(1));
        mvc.perform(post("/api/goods/" + goodsId + "/views")).andExpect(status().isOk()).andExpect(jsonPath("$.view_count").value(2));
        mvc.perform(put("/api/me/favorites/" + goodsId).header("Authorization", "Bearer " + buyer)).andExpect(status().isOk());
        mvc.perform(get("/api/me/favorites").header("Authorization", "Bearer " + buyer)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(goodsId));
        mvc.perform(delete("/api/me/favorites/" + goodsId).header("Authorization", "Bearer " + buyer)).andExpect(status().isOk());
        mvc.perform(get("/api/categories")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("教材资料"));

        long conversationId = body(mvc.perform(post("/api/conversations").header("Authorization", "Bearer " + buyer)
                .contentType("application/json").content(json.writeValueAsString(Map.of("goodsId", goodsId))))
                .andExpect(status().isOk()).andReturn()).get("id").asLong();
        mvc.perform(get("/api/conversations").header("Authorization", "Bearer " + buyer)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unread_count").value(0));
        mvc.perform(post("/api/conversations/" + conversationId + "/messages").header("Authorization", "Bearer " + seller)
                .contentType("application/json").characterEncoding("UTF-8").content("{\"content\":\"你好，我来确认商品细节\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/conversations").header("Authorization", "Bearer " + buyer)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unread_count").value(1));
        JsonNode chat = body(mvc.perform(get("/api/conversations/" + conversationId + "/messages").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isOk()).andReturn());
        assertEquals("你好，我来确认商品细节", chat.get(0).get("content").asText());
        long chatMessageId = chat.get(0).get("id").asLong();
        mvc.perform(put("/api/conversations/" + conversationId + "/read").header("Authorization", "Bearer " + buyer)
                .contentType("application/json").content(json.writeValueAsString(Map.of("messageId", chatMessageId))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/conversations").header("Authorization", "Bearer " + buyer)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unread_count").value(0));
        mvc.perform(get("/api/conversations/" + conversationId + "/messages").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());

        String intention = json.writeValueAsString(Map.of("goodsId", goodsId, "message", "Delivery please"));
        mvc.perform(post("/api/orders/intentions").header("Authorization", "Bearer " + seller)
                .contentType("application/json").content(intention)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders/intentions").header("Authorization", "Bearer " + buyer)
                .contentType("application/json").content(intention)).andExpect(status().isOk());
        mvc.perform(post("/api/orders/intentions").header("Authorization", "Bearer " + buyer)
                .contentType("application/json").content(intention)).andExpect(status().isConflict());
        long orderId = db.queryForObject("select id from order_intention where goods_id=?", Long.class, goodsId);
        mvc.perform(put("/api/orders/" + orderId + "/ACCEPT").header("Authorization", "Bearer " + buyer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/" + orderId + "/review").header("Authorization", "Bearer " + buyer)
                .contentType("application/json").content("{\"rating\":4,\"content\":\"Good\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/orders/" + orderId + "/ACCEPT").header("Authorization", "Bearer " + seller)).andExpect(status().isOk());
        assertEquals("RESERVED", db.queryForObject("select trade_status from goods where id=?", String.class, goodsId));
        mvc.perform(put("/api/orders/" + orderId + "/COMPLETE").header("Authorization", "Bearer " + seller)).andExpect(status().isForbidden());
        mvc.perform(put("/api/orders/" + orderId + "/COMPLETE").header("Authorization", "Bearer " + buyer)).andExpect(status().isConflict());
        mvc.perform(proof(orderId, "buyer-ref-" + suffix).header("Authorization", "Bearer " + seller)).andExpect(status().isForbidden());
        mvc.perform(proof(orderId, "buyer-ref-" + suffix).header("Authorization", "Bearer " + buyer)).andExpect(status().isOk());
        mvc.perform(proof(orderId, "buyer-ref-" + suffix).header("Authorization", "Bearer " + buyer)).andExpect(status().isConflict());
        mvc.perform(get("/api/orders/" + orderId + "/payment-proof")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders/" + orderId + "/payment-proof").header("Authorization", "Bearer " + seller)).andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/" + orderId + "/payment-proof").header("Authorization", "Bearer " + buyer)).andExpect(status().isOk());
        mvc.perform(put("/api/orders/" + orderId + "/COMPLETE").header("Authorization", "Bearer " + buyer)).andExpect(status().isConflict());
        String verification = json.writeValueAsString(Map.of("action", "CONFIRM", "reference", "receipt-" + suffix, "actualAmount", 0.25, "note", "Checked actual statement"));
        mvc.perform(put("/api/admin/payments/" + orderId).header("Authorization", "Bearer " + buyer).contentType("application/json").content(verification)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/payments/" + orderId).header("Authorization", "Bearer " + admin).contentType("application/json").content(verification.replace("0.25", "12.5"))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/payments/" + orderId).header("Authorization", "Bearer " + admin).contentType("application/json").content(verification)).andExpect(status().isOk());
        mvc.perform(put("/api/admin/payments/" + orderId).header("Authorization", "Bearer " + admin).contentType("application/json").content(verification)).andExpect(status().isConflict());
        mvc.perform(put("/api/orders/" + orderId + "/COMPLETE").header("Authorization", "Bearer " + buyer)).andExpect(status().isOk());
        mvc.perform(put("/api/orders/" + orderId + "/COMPLETE").header("Authorization", "Bearer " + buyer)).andExpect(status().isConflict());
        assertEquals("SOLD", db.queryForObject("select trade_status from goods where id=?", String.class, goodsId));
        mvc.perform(post("/api/orders/" + orderId + "/review").header("Authorization", "Bearer " + buyer)
                .contentType("application/json").content("{\"rating\":6}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders/" + orderId + "/review").header("Authorization", "Bearer " + buyer)
                .contentType("application/json").content("{\"rating\":4,\"content\":\"Good\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + orderId + "/review").header("Authorization", "Bearer " + buyer)
                .contentType("application/json").content("{\"rating\":5}")).andExpect(status().isConflict());
        JsonNode reputation = body(mvc.perform(get("/api/me/credit").header("Authorization", "Bearer " + seller))
                .andExpect(status().isOk()).andReturn());
        assertEquals(4.0, reputation.get("seller_credit").asDouble());
        assertEquals(1, reputation.get("completed_trades").asInt());
        assertEquals(1, reputation.get("review_count").asInt());
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + buyer)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rating").value(4));
        mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + buyer)).andExpect(status().isOk()).andExpect(jsonPath("$.paidFees").value(0.25));
        mvc.perform(get("/api/admin/revenue").header("Authorization", "Bearer " + admin)).andExpect(status().isOk()).andExpect(jsonPath("$.settledFees").value(0.25));

        long cancelledGoods = body(mvc.perform(post("/api/goods").header("Authorization", "Bearer " + seller)
                .contentType("application/json").content(goodsBody)).andExpect(status().isOk()).andReturn()).get("id").asLong();
        mvc.perform(put("/api/admin/audits/GOODS/" + cancelledGoods).param("status", "APPROVED").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mvc.perform(post("/api/orders/intentions").header("Authorization", "Bearer " + buyer).contentType("application/json")
                .content(json.writeValueAsString(Map.of("goodsId", cancelledGoods)))).andExpect(status().isOk());
        long cancelledOrder = db.queryForObject("select id from order_intention where goods_id=?", Long.class, cancelledGoods);
        mvc.perform(put("/api/orders/" + cancelledOrder + "/ACCEPT").header("Authorization", "Bearer " + seller)).andExpect(status().isOk());
        mvc.perform(proof(cancelledOrder, "buyer-ref-" + suffix).header("Authorization", "Bearer " + buyer)).andExpect(status().isConflict());
        mvc.perform(proof(cancelledOrder, "other-" + suffix).header("Authorization", "Bearer " + buyer)).andExpect(status().isOk());
        mvc.perform(put("/api/admin/payments/" + cancelledOrder).header("Authorization", "Bearer " + admin).contentType("application/json").content(verification)).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/payments/" + cancelledOrder).header("Authorization", "Bearer " + admin).contentType("application/json").content(verification.replace("receipt-", "second-receipt-"))).andExpect(status().isOk());
        mvc.perform(put("/api/orders/" + cancelledOrder + "/CANCEL").header("Authorization", "Bearer " + buyer)).andExpect(status().isOk());
        assertEquals("REFUND_PENDING", db.queryForObject("select status from service_fee where order_id=?", String.class, cancelledOrder));
        mvc.perform(put("/api/admin/payments/" + cancelledOrder).header("Authorization", "Bearer " + admin).contentType("application/json")
                .content("{\"action\":\"NO_PAYMENT\",\"note\":\"Cannot erase confirmed funds\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/payments/" + cancelledOrder).header("Authorization", "Bearer " + admin).contentType("application/json")
                .content(verification.replace("CONFIRM", "REFUND").replace("receipt-", "refund-"))).andExpect(status().isOk());
        assertEquals("REFUNDED", db.queryForObject("select status from service_fee where order_id=?", String.class, cancelledOrder));
        db.update("update sys_user set status='DISABLED' where username=?", buyerName);
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + buyer)).andExpect(status().isUnauthorized());
    }
}
