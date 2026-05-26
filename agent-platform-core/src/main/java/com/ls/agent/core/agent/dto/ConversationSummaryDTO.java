package com.ls.agent.core.agent.dto;

import java.time.LocalDateTime;

public record ConversationSummaryDTO(
        Long conversationId,
        Long applicationId,
        Long profileId,
        String title,
        String channel,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
