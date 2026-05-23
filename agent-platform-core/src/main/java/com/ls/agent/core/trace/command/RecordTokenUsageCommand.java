package com.ls.agent.core.trace.command;

public record RecordTokenUsageCommand(
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
        Boolean estimated
) {
}
