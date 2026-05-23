package com.ls.agent.core.quota.dto;
public record TokenUsageTopModelDTO(
        Long modelConfigId,
        String modelName,
        String providerType,
        Integer requestCount,
        Integer totalTokens
) {
}
