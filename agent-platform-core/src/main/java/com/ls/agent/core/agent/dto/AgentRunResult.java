package com.ls.agent.core.agent.dto;

import com.ls.agent.core.model.dto.ModelUsageDTO;

public record AgentRunResult(
        Long conversationId,
        String assistantMessage,
        ModelUsageDTO usage
) {
}
