package com.ls.agent.web.security;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 全局配置类。
 * 定义了过滤器链、接口访问权限、会话策略以及异常处理。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /**
     * 配置安全过滤链。
     * 
     * @param http HttpSecurity 配置对象
     * @param jwtAuthenticationFilter JWT 身份验证过滤器
     * @param authenticationEntryPoint 未登录处理入口
     * @param accessDeniedHandler 无权限处理处理器
     * @return 配置好的 SecurityFilterChain
     * @throws Exception 配置异常
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        return http
                // 1. 禁用 CSRF，因为我们使用 JWT，不需要 Session 相关的防御
                .csrf(AbstractHttpConfigurer::disable)
                // 2. 设置会话策略为无状态（Stateless），不使用 HTTP Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 3. 配置异常处理
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint) // 处理未认证（未登录）
                        .accessDeniedHandler(accessDeniedHandler))         // 处理无权限
                // 4. 配置接口权限控制规则
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // 注册和登录接口允许匿名访问
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        // 管理员相关接口需要具备 ADMIN 角色
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 其余所有请求都需要经过身份认证
                        .anyRequest().authenticated())
                // 5. 在标准用户名密码验证过滤器之前插入 JWT 验证过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
