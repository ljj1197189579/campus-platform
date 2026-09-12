package com.campus.platform.config;

import com.campus.platform.repository.PlatformRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class JwtFilter extends OncePerRequestFilter {
    public static final String SECRET = secret();
    public static final String USER = "CURRENT_USER";
    private final PlatformRepository repo;

    public JwtFilter(PlatformRepository repo) {
        this.repo = repo;
    }

    private static String secret() {
        String configured = System.getenv("JWT_SECRET");
        if (configured != null) {
            if (configured.getBytes(StandardCharsets.UTF_8).length < 32)
                throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
            return configured;
        }
        byte[] bytes = new byte[48];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        boolean publicRequest = method.equals("OPTIONS")
                || (method.equals("POST") && path.matches("/api/goods/[0-9]+/views"))
                || (method.equals("POST") && (path.equals("/api/auth/login") || path.equals("/api/auth/register")))
                || (method.equals("GET") && (path.equals("/api/categories") || path.equals("/api/goods") || path.equals("/api/lost-found") || path.equals("/api/payment-settings") || path.startsWith("/api/media/")));
        if (publicRequest) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            reject(response, 401, "请先登录");
            return;
        }
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .build().parseSignedClaims(header.substring(7)).getPayload();
            if (!(claims.get("uid") instanceof Number) || claims.getExpiration() == null)
                throw new IllegalArgumentException("Invalid token claims");
        } catch (JwtException | IllegalArgumentException exception) {
            reject(response, 401, "登录已失效，请重新登录");
            return;
        }
        try {
            Map<String, Object> account = repo.activeUser(((Number) claims.get("uid")).longValue());
            if (!account.get("role").equals(claims.get("role")) || !account.get("username").equals(claims.getSubject())) {
                reject(response, 401, "账号权限已变化，请重新登录");
                return;
            }
        } catch (EmptyResultDataAccessException exception) {
            reject(response, 401, "账号不可用，请重新登录");
            return;
        } catch (DataAccessException exception) {
            reject(response, 503, "服务暂时不可用，请稍后重试");
            return;
        }
        request.setAttribute(USER, claims);
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}

