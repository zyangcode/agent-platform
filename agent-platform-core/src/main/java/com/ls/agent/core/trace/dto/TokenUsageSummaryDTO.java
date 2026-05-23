package com.ls.agent.core.trace.dto;

import java.util.List;

public record TokenUsageSummaryDTO(
        Long applicationId,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Integer requestCount,
        Integer estimatedCount,
        Integer realUsageCount,
        List<TokenUsageTopModelDTO> topModels
) {
    public TokenUsageSummaryDTO {
        topModels = topModels == null ? List.of() : List.copyOf(topModels);
    }
}
