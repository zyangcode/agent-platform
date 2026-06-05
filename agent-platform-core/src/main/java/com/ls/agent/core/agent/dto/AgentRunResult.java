package com.ls.agent.core.agent.dto;

import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;

import java.util.List;

public record AgentRunResult(
        Long conversationId,
        String assistantMessage,
        ModelUsageDTO usage,
        List<AgentToolEventDTO> toolEvents,
        List<RagSearchResultDTO> ragCitations
) {
    public AgentRunResult(Long conversationId, String assistantMessage, ModelUsageDTO usage) {
        this(conversationId, assistantMessage, usage, List.of(), List.of());
    }

    public AgentRunResult(Long conversationId, String assistantMessage, ModelUsageDTO usage, List<AgentToolEventDTO> toolEvents) {
        this(conversationId, assistantMessage, usage, toolEvents, List.of());
    }

    public AgentRunResult {
        toolEvents = toolEvents == null ? List.of() : List.copyOf(toolEvents);
        ragCitations = ragCitations == null ? List.of() : List.copyOf(ragCitations);
    }
}
