package com.ls.agent.core.trace.dto;

import java.time.LocalDateTime;

public record TokenUsageDTO(
        Long id,
        String traceId,
        Long spanId,
        Long tenantId,
        Long applicationId,
        Long userId,
        Long profileId,
        Long modelConfigId,
        Long providerId,
        String modelName,
        String providerType,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Boolean estimated,
        LocalDateTime createdAt
) {
}
