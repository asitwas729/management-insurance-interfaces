package com.example.interfacehub.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenBlacklistService {

    private final JdbcTemplate jdbcTemplate;

    public TokenBlacklistService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void blacklist(String token, LocalDateTime expiresAt) {
        String hash = sha256(token);
        jdbcTemplate.update(
            "INSERT INTO token_blacklist (token_hash, expires_at) VALUES (?, ?) ON CONFLICT (token_hash) DO NOTHING",
            hash, expiresAt
        );
    }

    @Transactional(readOnly = true)
    public boolean isBlacklisted(String token) {
        String hash = sha256(token);
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM token_blacklist WHERE token_hash = ? AND expires_at > CURRENT_TIMESTAMP",
            Integer.class, hash
        );
        return count != null && count > 0;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
