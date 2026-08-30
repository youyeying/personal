package com.personal.backend.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：生成 / 解析 Token
 * 载荷：userId, username；有效期可配置
 */
@Component
public class JwtUtils {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expire-minutes}")
    private long expireMinutes;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 生成 Token（accessToken：短期，默认 15 分钟） */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireMinutes * 60_000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(expire)
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析 Token，返回 Claims
     * 失败（过期 / 签名错误 / 格式非法）抛出异常，由拦截器统一转 401
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从 Claims 取用户 id */
    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }
}
