package com.ls.agent.core.model.dto;

public record ModelUsageDTO(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        boolean estimated
) {
}
