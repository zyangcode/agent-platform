package com.ls.agent.core.agent.command;

import java.util.List;
import java.util.function.Consumer;

public record AgentRunCommand(
        Long tenantId,
        Long userId,
        Long applicationId,
        Long profileId,
        Long conversationId,
        String userInput,
        String traceId,
        List<Long> selectedSkillIds,
        List<Long> selectedMcpToolIds,
        Integer maxContextTokens,
        String agentMode,
        List<String> confirmedToolKeys,
        PendingToolCallCommand pendingToolCall,
        Consumer<String> progressCallback
) {
    public AgentRunCommand {
        selectedSkillIds = selectedSkillIds == null ? null : List.copyOf(selectedSkillIds);
        selectedMcpToolIds = selectedMcpToolIds == null ? null : List.copyOf(selectedMcpToolIds);
        confirmedToolKeys = confirmedToolKeys == null ? List.of() : List.copyOf(confirmedToolKeys);
    }

    public AgentRunCommand(
            Long tenantId,
            Long userId,
            Long applicationId,
            Long profileId,
            Long conversationId,
            String userInput,
            String traceId,
            List<Long> selectedSkillIds,
            List<Long> selectedMcpToolIds,
            Integer maxContextTokens,
            String agentMode
    ) {
        this(
                tenantId, userId, applicationId, profileId, conversationId,
                userInput, traceId, selectedSkillIds, selectedMcpToolIds,
                maxContextTokens, agentMode, List.of(), null, null
        );
    }

    public AgentRunCommand(
            Long tenantId,
            Long userId,
            Long applicationId,
            Long profileId,
            Long conversationId,
            String userInput,
            String traceId,
            List<Long> selectedSkillIds,
            List<Long> selectedMcpToolIds,
            Integer maxContextTokens
    ) {
        this(
                tenantId, userId, applicationId, profileId, conversationId,
                userInput, traceId, selectedSkillIds, selectedMcpToolIds,
                maxContextTokens, null, List.of(), null, null
        );
    }

    public AgentRunCommand(
            Long tenantId,
            Long userId,
            Long applicationId,
            Long profileId,
            Long conversationId,
            String userInput,
            String traceId,
            List<Long> selectedSkillIds,
            List<Long> selectedMcpToolIds,
            Integer maxContextTokens,
            String agentMode,
            List<String> confirmedToolKeys
    ) {
        this(
                tenantId, userId, applicationId, profileId, conversationId,
                userInput, traceId, selectedSkillIds, selectedMcpToolIds,
                maxContextTokens, agentMode, confirmedToolKeys, null, null
        );
    }
}
