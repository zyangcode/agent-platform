package com.ls.agent.gateway.dto;

import java.util.Map;

public record SseEventPayload(
        String type,
        String traceId,
        Long conversationId,
        int step,
        String content,
        Map<String, Object> metadata
) {
}
