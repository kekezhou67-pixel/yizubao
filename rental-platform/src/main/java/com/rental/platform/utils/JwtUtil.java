package com.rental.platform.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire}")
    private long expire;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT Token
     * <p>
     * jjwt 0.12.x 起，setXxx 风格的 builder 方法改成了简洁命名：
     * setSubject → subject、setIssuedAt → issuedAt、setExpiration → expiration。
     */
    public String createToken(Long userId, String username, String role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expire);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析并验证 JWT Token，返回 Claims
     * <p>
     * jjwt 0.12.x 起：parserBuilder() → parser()，
     * setSigningKey() → verifyWith()，parseClaimsJws() → parseSignedClaims()，
     * getBody() → getPayload()。
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 提取用户ID
     */
    public Long getUserIdFromToken(String token) {
        String subject = parseToken(token).getSubject();
        return Long.valueOf(subject);
    }
}

