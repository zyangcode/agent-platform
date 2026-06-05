package com.ls.agent.core.context.command;

import java.util.List;

public record BuildAgentContextCommand(
        Long tenantId,
        Long userId,
        Long applicationId,
        Long profileId,
        Long conversationId,
        String userInput,
        Integer maxContextTokens,
        List<Long> selectedSkillIds,
        List<Long> selectedMcpToolIds,
        String traceId,
        Long parentSpanId
) {
    public BuildAgentContextCommand {
        selectedSkillIds = selectedSkillIds == null ? null : List.copyOf(selectedSkillIds);
        selectedMcpToolIds = selectedMcpToolIds == null ? null : List.copyOf(selectedMcpToolIds);
    }

    public BuildAgentContextCommand(
            Long tenantId,
            Long userId,
            Long applicationId,
            Long profileId,
            Long conversationId,
            String userInput,
            Integer maxContextTokens,
            List<Long> selectedSkillIds,
            List<Long> selectedMcpToolIds
    ) {
        this(
                tenantId,
                userId,
                applicationId,
                profileId,
                conversationId,
                userInput,
                maxContextTokens,
                selectedSkillIds,
                selectedMcpToolIds,
                null,
                null
        );
    }
}
