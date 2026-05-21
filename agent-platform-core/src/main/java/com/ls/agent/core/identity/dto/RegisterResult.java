package com.ls.agent.core.identity.dto;

public record RegisterResult(
        Long userId,
        Long tenantId,
        String username,
        String displayName
) {
}
