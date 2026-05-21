package com.ls.agent.web.security;

import com.ls.agent.core.identity.dto.CurrentUserDTO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * JWT 令牌服务类。
 * 负责 JWT 的生成、解析、校验以及签名密钥的管理。
 */
@Service
public class JwtTokenService {

    /** JWT 配置属性 */
    private final JwtProperties properties;

    /**
     * 构造函数注入配置。
     */
    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
    }

    /**
     * 为登录用户生成 JWT 令牌。
     *
     * @param user 用户信息 DTO
     * @return 签名的 JWT 字符串
     */
    public String generate(CurrentUserDTO user) {
        Instant now = Instant.now();
        // 使用 JJWT 库构造 Token
        return Jwts.builder()
                .subject(String.valueOf(user.userId())) // 主题设为用户 ID
                .claim("tenantId", user.tenantId())     // 存储租户 ID
                .claim("username", user.username())     // 存储用户名
                .claim("displayName", user.displayName()) // 存储显示名称
                .claim("roles", user.roles())           // 存储角色列表
                .issuedAt(Date.from(now))               // 签发时间
                .expiration(Date.from(now.plusSeconds(properties.getExpiresInSeconds()))) // 过期时间
                .signWith(signingKey())                 // 使用密钥签名
                .compact();                             // 压缩生成最终字符串
    }

    /**
     * 解析并验证 JWT 令牌。
     *
     * @param token 原始 JWT 字符串
     * @return 解析后的当前用户信息对象
     * @throws io.jsonwebtoken.JwtException 如果 Token 无效或过期
     */
    public CurrentUser parse(String token) {
        // 1. 解析并验证签名
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // 2. 从载荷 (Payload) 中提取字段
        Long userId = Long.valueOf(claims.getSubject());
        Long tenantId = ((Number) claims.get("tenantId")).longValue();
        String username = claims.get("username", String.class);
        String displayName = claims.get("displayName", String.class);
        List<String> roles = claims.get("roles", List.class);
        
        // 3. 构造 CurrentUser 模型对象
        return new CurrentUser(userId, tenantId, username, displayName, roles);
    }

    /**
     * 获取 Token 的有效期（秒）。
     */
    public long expiresInSeconds() {
        return properties.getExpiresInSeconds();
    }

    /**
     * 获取签名的 SecretKey。
     */
    private SecretKey signingKey() {
        // 将配置文件中的明文字符串转换为 HMAC-SHA 算法所需的 SecretKey
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
