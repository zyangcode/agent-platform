package com.ls.agent.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 身份验证过滤器。
 * 拦截请求，解析 Authorization 头中的 JWT 令牌，并构建 SecurityContext 认证信息。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** JWT 令牌业务服务 */
    private final JwtTokenService jwtTokenService;

    /**
     * 构造函数注入。
     */
    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * 执行过滤器核心逻辑。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 1. 从请求头中获取 Authorization 字段
        String authorization = request.getHeader("Authorization");
        
        // 2. 判断是否符合 "Bearer {token}" 格式
        if (authorization != null && authorization.startsWith("Bearer ")) {
            // 3. 提取 Token 字符串
            String token = authorization.substring("Bearer ".length());
            try {
                // 4. 调用服务解析 Token 得到当前用户信息 (CurrentUser)
                CurrentUser currentUser = jwtTokenService.parse(token);
                
                // 5. 将解析出的用户信息封装为 Spring Security 的 Authentication 对象
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        currentUser, // Principal: 当前用户对象
                        null,        // Credentials: 密码（已在 Token 中验证，此处填 null）
                        currentUser.roles().stream()
                                // 将角色列表转换为 Spring Security 要求的 GrantedAuthority 列表 (需加 ROLE_ 前缀)
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                                .toList()
                );
                
                // 6. 将认证信息存入当前线程的安全上下文 (SecurityContext)
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ignored) {
                // 如果解析失败（过期、篡改等），清除上下文确保安全
                SecurityContextHolder.clearContext();
            }
        }
        
        // 7. 继续执行过滤器链中的下一个过滤器
        filterChain.doFilter(request, response);
    }
}
