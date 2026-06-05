package com.ls.agent.core.context.dto;

import com.ls.agent.core.mcp.dto.McpToolDTO;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;
import com.ls.agent.core.skill.dto.SkillDTO;

import java.util.List;

public record AgentContextDTO(
        Long modelConfigId,
        ProfileDTO profile,
        List<ModelMessage> conversationMessages,
        List<ModelMessage> apiMessages,
        List<SkillDTO> availableSkills,
        List<McpToolDTO> availableMcpTools,
        int estimatedTokens,
        boolean truncated,
        ContextBudgetSnapshotDTO contextBudgetSnapshot,
        List<RagSearchResultDTO> ragSearchResults
) {
    public AgentContextDTO {
        conversationMessages = conversationMessages == null ? List.of() : List.copyOf(conversationMessages);
        apiMessages = apiMessages == null ? List.of() : List.copyOf(apiMessages);
        availableSkills = availableSkills == null ? List.of() : List.copyOf(availableSkills);
        availableMcpTools = availableMcpTools == null ? List.of() : List.copyOf(availableMcpTools);
        contextBudgetSnapshot = contextBudgetSnapshot == null
                ? ContextBudgetSnapshotDTO.minimal(0, estimatedTokens, truncated)
                : contextBudgetSnapshot;
        ragSearchResults = ragSearchResults == null ? List.of() : List.copyOf(ragSearchResults);
    }

    public AgentContextDTO(
            Long modelConfigId,
            ProfileDTO profile,
            List<ModelMessage> messages,
            List<SkillDTO> availableSkills,
            List<McpToolDTO> availableMcpTools,
            int estimatedTokens,
            boolean truncated
    ) {
        this(
                modelConfigId, profile, messages, messages,
                availableSkills, availableMcpTools,
                estimatedTokens, truncated,
                ContextBudgetSnapshotDTO.minimal(0, estimatedTokens, truncated),
                List.of()
        );
    }

    public AgentContextDTO(
            Long modelConfigId,
            ProfileDTO profile,
            List<ModelMessage> conversationMessages,
            List<ModelMessage> apiMessages,
            List<SkillDTO> availableSkills,
            List<McpToolDTO> availableMcpTools,
            int estimatedTokens,
            boolean truncated
    ) {
        this(
                modelConfigId, profile, conversationMessages, apiMessages,
                availableSkills, availableMcpTools,
                estimatedTokens, truncated,
                ContextBudgetSnapshotDTO.minimal(0, estimatedTokens, truncated),
                List.of()
        );
    }

    /**
     * Backward-compatible accessor for existing callers. New single-agent code should prefer apiMessages().
     */
    public List<ModelMessage> messages() {
        return apiMessages;
    }
}
