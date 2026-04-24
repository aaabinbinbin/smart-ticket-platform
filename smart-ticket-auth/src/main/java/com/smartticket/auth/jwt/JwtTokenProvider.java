package com.smartticket.auth.jwt;

import com.smartticket.auth.model.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * JWT 令牌生成和解析组件。
 *
 * <p>令牌中只放认证必要信息：用户 ID、用户名和基础角色。不要把工单级权限结果写进 JWT。</p>
 */
@Component
public class JwtTokenProvider {
    /** JWT 签名密钥。 */
    private final SecretKey secretKey;
    /** 访问令牌有效期。 */
    private final Duration expiration;

    /**
     * 构造JWT令牌提供器。
     */
    public JwtTokenProvider(
            @Value("${smart-ticket.jwt.secret}") String secret,
            @Value("${smart-ticket.jwt.expiration-minutes}") long expirationMinutes
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    /**
     * 为认证通过的用户生成访问令牌。
     */
    public String generateToken(AuthUser authUser) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + expiration.toMillis());
        List<String> roles = authUser.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.replace("ROLE_", ""))
                .toList();

        return Jwts.builder()
                .subject(authUser.getUsername())
                .claim("userId", authUser.getUserId())
                .claim("realName", authUser.getRealName())
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 从令牌中解析用户名。
     */
    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * 校验令牌签名和过期时间。
     */
    public boolean validateToken(String token) {
        parseClaims(token);
        return true;
    }

    /**
     * 返回令牌过期秒数，供登录响应展示。
     */
    public long getExpirationSeconds() {
        return expiration.toSeconds();
    }

    /**
     * 解析Claims。
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
