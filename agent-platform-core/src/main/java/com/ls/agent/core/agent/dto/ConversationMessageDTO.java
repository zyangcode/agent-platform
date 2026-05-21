package com.ls.agent.core.agent.dto;

public record ConversationMessageDTO(
        String role,
        String content,
        Integer tokenCount
) {
}
