package com.campus.platform.service;

import com.campus.platform.config.JwtFilter;
import com.campus.platform.repository.PlatformRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthService {
    private static final int ITERATIONS = 120000;
    private static final int KEY_LENGTH = 256;
    private final PlatformRepository repo;
    private final SecureRandom random = new SecureRandom();

    public AuthService(PlatformRepository repo) {
        this.repo = repo;
    }

    public Map<String, Object> login(String username, String password) {
        validate(username, password);
        Map<String, Object> account = repo.user(username);
        if (!matches(password, String.valueOf(account.get("password_hash")))) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String stored = String.valueOf(account.get("password_hash"));
        if (!stored.startsWith("pbkdf2$")) {
            repo.upgradePassword(((Number) account.get("id")).longValue(), stored, hash(password));
        }
        String token = Jwts.builder()
                .subject(username)
                .claim("uid", account.get("id"))
                .claim("role", account.get("role"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(Keys.hmacShaKeyFor(JwtFilter.SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", account.get("id"));
        user.put("username", account.get("username"));
        user.put("nickname", account.get("nickname"));
        user.put("role", account.get("role"));
        user.put("creditScore", null);
        user.put("completedTrades", 0);
        return Map.of("token", token, "user", user);
    }

    public void register(String username, String password) {
        validate(username, password);
        if (password.length() < 6) {
            throw new IllegalArgumentException("密码至少 6 位");
        }
        repo.user(username, hash(password), username);
    }

    private void validate(String username, String password) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,50}") || password == null || password.length() < 6 || password.length() > 128)
            throw new IllegalArgumentException("账号需为3-50位字母数字下划线，密码需为6-128位");
    }

    private String hash(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return "pbkdf2$" + Base64.getEncoder().encodeToString(salt) + "$" + digest(password, salt);
    }

    private boolean matches(String password, String stored) {
        if (!stored.startsWith("pbkdf2$")) {
            return password.equals(stored);
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 3) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            return MessageDigest.isEqual(digest(password, salt).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String digest(String password, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(encoded);
        } catch (Exception e) {
            throw new IllegalStateException("密码处理失败", e);
        }
    }
}
