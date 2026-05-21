package com.ls.agent.core.identity.dto;

import java.time.LocalDateTime;

public record ApiKeyDTO(
        Long apiKeyId,
        String name,
        String keyPrefix,
        String status,
        LocalDateTime lastUsedAt,
        LocalDateTime createdAt
) {
}
