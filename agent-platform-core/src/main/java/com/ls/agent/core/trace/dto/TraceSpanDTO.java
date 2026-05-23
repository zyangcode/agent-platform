package com.ls.agent.core.trace.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record TraceSpanDTO(
        Long id,
        String traceId,
        Long parentSpanId,
        String spanName,
        String spanType,
        String component,
        String status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long latencyMs,
        String errorCode,
        String errorMessage,
        JsonNode attributes,
        LocalDateTime createdAt
) {
}
