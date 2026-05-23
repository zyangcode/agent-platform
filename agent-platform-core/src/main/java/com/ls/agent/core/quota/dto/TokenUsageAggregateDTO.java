package com.ls.agent.core.quota.dto;

public record TokenUsageAggregateDTO(
        Integer totalTokens,
        Boolean estimated
) {
}
