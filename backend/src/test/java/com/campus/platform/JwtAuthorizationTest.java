package com.campus.platform;

import com.campus.platform.config.JwtFilter;
import com.campus.platform.repository.PlatformRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthorizationTest {
    private PlatformRepository repo;
    private JwtFilter filter;
    private FilterChain chain;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach void setup() {
        repo = mock(PlatformRepository.class);
        filter = new JwtFilter(repo);
        chain = mock(FilterChain.class);
        request = new MockHttpServletRequest("GET", "/api/orders");
        response = new MockHttpServletResponse();
    }

    private void token(String role, long expiresIn) {
        String token = Jwts.builder().subject("student").claim("uid", 10L).claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + expiresIn))
                .signWith(Keys.hmacShaKeyFor(JwtFilter.SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        request.addHeader("Authorization", "Bearer " + token);
    }

    @Test void missingTokenCannotAccessOrders() throws Exception {
        filter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain, repo);
    }

    @Test void expiredTokenIsRejected() throws Exception {
        token("USER", -60000);
        filter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain, repo);
    }

    @Test void malformedTokenIsRejected() throws Exception {
        request.addHeader("Authorization", "Bearer invalid");
        filter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain, repo);
    }

    @Test void disabledAccountCannotReuseToken() throws Exception {
        token("USER", 60000);
        when(repo.activeUser(10)).thenThrow(new EmptyResultDataAccessException(1));
        filter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test void revokedAdminRoleCannotReuseToken() throws Exception {
        token("ADMIN", 60000);
        when(repo.activeUser(10)).thenReturn(Map.of("username", "student", "role", "USER"));
        filter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test void databaseOutageFailsClosed() throws Exception {
        token("USER", 60000);
        when(repo.activeUser(10)).thenThrow(new DataAccessResourceFailureException("offline"));
        filter.doFilter(request, response, chain);
        assertEquals(503, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test void enabledAccountCanContinue() throws Exception {
        token("USER", 60000);
        when(repo.activeUser(10)).thenReturn(Map.of("username", "student", "role", "USER"));
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        assertNotNull(request.getAttribute(JwtFilter.USER));
    }

    @Test void publicReadDoesNotRequireToken() throws Exception {
        request.setRequestURI("/api/goods");
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(repo);
    }
}
