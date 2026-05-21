package com.ls.agent.core.model.dto;

public record ModelInvokeResult(
        Long modelConfigId,
        String modelName,
        String assistantMessage,
        ModelUsageDTO usage
) {
}
