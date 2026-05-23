package com.ls.agent.core.model.dto;

public record ModelInvokeResult(
        Long modelConfigId,
        Long providerId,
        String providerType,
        String modelName,
        String assistantMessage,
        ModelUsageDTO usage
) {
}
