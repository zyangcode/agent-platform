package com.ls.agent.core.model.dto;

import java.util.List;

public record ModelInvokeResult(
        Long modelConfigId,
        Long providerId,
        String providerType,
        String modelName,
        String assistantMessage,
        ModelUsageDTO usage,
        List<ModelToolCallDTO> toolCalls
) {

    public ModelInvokeResult(
            Long modelConfigId,
            Long providerId,
            String providerType,
            String modelName,
            String assistantMessage,
            ModelUsageDTO usage
    ) {
        this(modelConfigId, providerId, providerType, modelName, assistantMessage, usage, List.of());
    }

    public ModelInvokeResult {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
