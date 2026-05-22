package com.ls.agent.core.identity.dto;

public record ApiKeyAuthResult(
        Long tenantId,
        Long applicationId,
        Long userId
) {
}
