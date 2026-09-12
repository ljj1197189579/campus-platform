package com.campus.platform;
import com.campus.platform.common.GoodsRequest;
import com.campus.platform.repository.PlatformRepository;
import com.campus.platform.service.AuthService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductionRegressionTest {
    @Test void databaseFailureNeverBecomesDemoLogin() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForMap(anyString(), any(Object[].class))).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThrows(DataAccessResourceFailureException.class, () -> new PlatformRepository(db).user("admin"));
    }
    @Test void newPasswordsAreSaltedAndCanAuthenticate() {
        PlatformRepository repo = mock(PlatformRepository.class);
        AuthService service = new AuthService(repo);
        service.register("student", "long-password");
        var capture = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repo).user(eq("student"), capture.capture(), eq("student"));
        String encoded = capture.getValue();
        assertTrue(encoded.startsWith("pbkdf2$"));
        assertFalse(encoded.contains("long-password"));
        when(repo.user("student")).thenReturn(Map.of("id", 10L, "username", "student", "nickname", "Student", "role", "USER", "password_hash", encoded));
        assertNotNull(service.login("student", "long-password").get("token"));
        assertThrows(IllegalArgumentException.class, () -> service.login("student", "wrong-password"));
    }
    @Test void nullPasswordDoesNotCauseServerError() {
        AuthService service = new AuthService(mock(PlatformRepository.class));
        assertThrows(IllegalArgumentException.class, () -> service.register("student", null));
        assertThrows(IllegalArgumentException.class, () -> service.login(null, "password"));
    }
    @Test void deliveryModeAndPriceAreValidated() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new GoodsRequest(1L, "Book", "Text", new BigDecimal("1.001"), "New", "INVALID", "", false, java.util.List.of("/photo"), null, "Library")).isEmpty());
            assertTrue(validator.validate(new GoodsRequest(1L, "Book", "Text", new BigDecimal("12.50"), "New", "DORM_DELIVERY", "Building 3", false, java.util.List.of("/photo"), new BigDecimal("25.00"), "Library")).isEmpty());
        }
    }
    @Test void repeatedAuditIsNotSuccessful() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        assertThrows(ResponseStatusException.class, () -> new PlatformRepository(db).audit("GOODS", 99, "APPROVED"));
    }
}
