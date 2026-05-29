package com.ls.agent.gateway.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record TeamSseEventPayload(
        String type,
        String traceId,
        Long conversationId,
        int step,
        String role,
        String taskId,
        String toolName,
        String status,
        String content,
        JsonNode payload
) {
}
