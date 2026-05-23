package com.ls.agent.core.trace.dto;

import java.time.LocalDateTime;

public record TraceSummaryDTO(
        String traceId,
        Long applicationId,
        Long userId,
        Long profileId,
        Long conversationId,
        String entrypoint,
        String agentMode,
        String status,
        Long latencyMs,
        Integer totalTokens,
        Boolean estimated,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
}
