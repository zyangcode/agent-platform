package com.ls.agent.core.identity.dto;

public record CreateApplicationResult(
        Long applicationId,
        String name,
        String status,
        CreatedApiKeyDTO apiKey
) {
}
