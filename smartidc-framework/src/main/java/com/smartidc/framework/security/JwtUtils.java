package com.smartidc.framework.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 工业级 JWT 权限凭证签发与校验工具 (JJWT 0.12.x 规范)
 * 承载 userId, username, tenantId, roleKey, assignedRooms 等不可篡改事实源
 */
@Component
public class JwtUtils {

    // 默认兜底高强度 512 位密钥 (至少 32 字节以满足 HS256/HS512 签名要求)
    private static final String DEFAULT_SECRET = "SmartIDC-Enterprise-AiOps-Mobile-Security-Token-SecretKey-2026-HighEntropy512Bit!";

    private final SecretKey secretKey;
    private final long accessTokenExpireMillis;

    public JwtUtils(
            @Value("${smartidc.jwt.secret:" + DEFAULT_SECRET + "}") String secret,
            @Value("${smartidc.jwt.access-token-expire-seconds:7200}") long expireSeconds) {
        String effectiveSecret = (secret == null || secret.trim().length() < 32) ? DEFAULT_SECRET : secret;
        this.secretKey = Keys.hmacShaKeyFor(effectiveSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpireMillis = expireSeconds * 1000L;
    }

    /**
     * 签发 2 小时有效期的业务 Access Token
     */
    public String generateAccessToken(Long userId, String username, String tenantId, String roleKey, Set<String> assignedRooms) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpireMillis);

        List<String> roomsList = (assignedRooms == null) ? Collections.emptyList() : new ArrayList<>(assignedRooms);

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("username", username)
                .claim("tenantId", tenantId != null ? tenantId : "000000")
                .claim("roleKey", roleKey != null ? roleKey : "engineer")
                .claim("rooms", roomsList)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 校验并解析 Token
     */
    public Claims parseToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 校验 Token 是否合法且未过期
     */
    public boolean validateToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return false;
        }
        Date expiration = claims.getExpiration();
        return expiration != null && expiration.after(new Date());
    }

    public Long getUserId(Claims claims) {
        if (claims == null) return null;
        Object val = claims.get("userId");
        if (val instanceof Number num) {
            return num.longValue();
        } else if (val != null) {
            try {
                return Long.parseLong(val.toString());
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public String getUsername(Claims claims) {
        return claims != null ? claims.get("username", String.class) : null;
    }

    public String getTenantId(Claims claims) {
        return claims != null ? claims.get("tenantId", String.class) : null;
    }

    public String getRoleKey(Claims claims) {
        return claims != null ? claims.get("roleKey", String.class) : null;
    }

    @SuppressWarnings("unchecked")
    public Set<String> getAssignedRooms(Claims claims) {
        if (claims == null) return Collections.emptySet();
        Object roomsObj = claims.get("rooms");
        if (roomsObj instanceof Collection<?> coll) {
            Set<String> set = new HashSet<>();
            for (Object item : coll) {
                if (item != null) set.add(item.toString());
            }
            return set;
        }
        return Collections.emptySet();
    }
}
