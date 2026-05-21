package com.ls.agent.core.identity.dto;

public record CreatedApiKeyDTO(
        Long apiKeyId,
        String name,
        String key,
        String keyPrefix,
        String status
) {
}
