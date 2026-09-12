package com.campus.platform;

import com.campus.platform.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderSecurityTest {
    private JdbcTemplate db;
    private OrderRepository repo;

    @BeforeEach void setup() {
        db = mock(JdbcTemplate.class);
        repo = new OrderRepository(db, mock(com.campus.platform.service.WalletService.class));
        when(db.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(7L);
    }

    private void order(String state, String trade) {
        when(db.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
                "id", 1L, "goods_id", 7L, "buyer_id", 10L, "seller_id", 20L,
                "status", state, "trade_status", trade, "audit_status", "APPROVED", "price", new java.math.BigDecimal("10.00")));
    }

    @Test void outsiderCannotChangeOrder() {
        order("PENDING", "ON_SALE");
        var failure = assertThrows(ResponseStatusException.class, () -> repo.transition(30, 1, "ACCEPT"));
        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode());
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test void buyerCannotAcceptOwnIntention() {
        order("PENDING", "ON_SALE");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, () -> repo.transition(10, 1, "ACCEPT")).getStatusCode());
    }

    @Test void acceptingReservesGoodsAndRejectsOtherIntentions() {
        order("PENDING", "ON_SALE");
        repo.transition(20, 1, "ACCEPT");
        verify(db).update("update goods set trade_status='RESERVED' where id=?", 7L);
        verify(db).update("update order_intention set status='ACCEPTED' where id=?", 1L);
        verify(db).update("update order_intention set status='REJECTED' where goods_id=? and id<>? and status='PENDING'", 7L, 1L);
    }

    @Test void sellerCannotConfirmReceipt() {
        order("ACCEPTED", "RESERVED");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, () -> repo.transition(20, 1, "COMPLETE")).getStatusCode());
    }

    @Test void receiptMarksOrderCompletedAndGoodsSold() {
        order("ACCEPTED", "RESERVED");
        repo.transition(10, 1, "COMPLETE");
        verify(db).update("update order_intention set status='COMPLETED' where id=?", 1L);
        verify(db).update("update goods set trade_status='SOLD' where id=?", 7L);
    }

    @Test void cancellationReleasesReservation() {
        order("ACCEPTED", "RESERVED");
        repo.transition(10, 1, "CANCEL");
        verify(db).update("update goods set trade_status='ON_SALE' where id=?", 7L);
    }

    @Test void completedOrderCannotBeCancelledOrCompletedAgain() {
        order("COMPLETED", "SOLD");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> repo.transition(10, 1, "CANCEL")).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> repo.transition(10, 1, "COMPLETE")).getStatusCode());
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test void reviewRequiresReceiptAndBuyerIdentity() {
        order("ACCEPTED", "RESERVED");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> repo.review(10, 1, 5, "ok")).getStatusCode());
        order("COMPLETED", "SOLD");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, () -> repo.review(20, 1, 5, "ok")).getStatusCode());
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test void duplicateReviewReturnsConflict() {
        order("COMPLETED", "SOLD");
        when(db.update(anyString(), any(Object[].class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> repo.review(10, 1, 5, "ok")).getStatusCode());
    }

    @Test void ratingOutsideScaleIsRejectedBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class, () -> repo.review(10, 1, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> repo.review(10, 1, 6, ""));
        verify(db, never()).queryForMap(anyString(), any(Object[].class));
    }

    @Test void completedOrderAcceptsOneBuyerReview() {
        order("COMPLETED", "SOLD");
        repo.review(10, 1, 4, "Good");
        verify(db).update("insert into seller_review(order_id,rating,content) values(?,?,?)", 1L, 4, "Good");
    }
}
