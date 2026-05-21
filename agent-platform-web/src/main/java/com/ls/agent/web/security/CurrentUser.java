package com.ls.agent.web.security;

import java.util.List;

/**
 * 当前登录用户信息模型。
 * 该对象通常由 {@link JwtAuthenticationFilter} 解析并存入 SecurityContext。
 *
 * @param userId 用户唯一标识
 * @param tenantId 所属租户 ID
 * @param username 登录用户名
 * @param displayName 用户显示名称
 * @param roles 用户拥有的角色代码列表（不带 ROLE_ 前缀）
 */
public record CurrentUser(
        Long userId,
        Long tenantId,
        String username,
        String displayName,
        List<String> roles
) {
}
