package com.ls.agent.core.agent.dto;

public record ConversationMessageDTO(
        Long messageId,
        String role,
        String content,
        Integer tokenCount,
        String traceId
) {
}
