package com.ls.agent.core.identity.dto;

public record RevokeApiKeyResult(
        Long apiKeyId,
        String status
) {
}
