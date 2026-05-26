package com.ls.agent.core.agent.dto;

import com.ls.agent.core.model.dto.ModelUsageDTO;

import java.util.List;

public record AgentRunResult(
        Long conversationId,
        String assistantMessage,
        ModelUsageDTO usage,
        List<AgentToolEventDTO> toolEvents
) {
    public AgentRunResult(Long conversationId, String assistantMessage, ModelUsageDTO usage) {
        this(conversationId, assistantMessage, usage, List.of());
    }

    public AgentRunResult {
        toolEvents = toolEvents == null ? List.of() : List.copyOf(toolEvents);
    }
}
