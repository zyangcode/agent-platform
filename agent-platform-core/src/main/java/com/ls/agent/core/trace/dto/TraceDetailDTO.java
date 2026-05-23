package com.ls.agent.core.trace.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

public record TraceDetailDTO(
        String traceId,
        Long tenantId,
        Long applicationId,
        Long userId,
        Long profileId,
        Long conversationId,
        String clientRequestId,
        String entrypoint,
        String agentMode,
        String status,
        String errorCode,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long latencyMs,
        JsonNode metadata,
        List<TraceSpanDTO> spans,
        List<TokenUsageDTO> tokenUsages
) {
    public TraceDetailDTO {
        spans = spans == null ? List.of() : List.copyOf(spans);
        tokenUsages = tokenUsages == null ? List.of() : List.copyOf(tokenUsages);
    }
}
