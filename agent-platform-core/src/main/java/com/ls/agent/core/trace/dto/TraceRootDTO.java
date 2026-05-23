package com.ls.agent.core.trace.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record TraceRootDTO(
        Long id,
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
