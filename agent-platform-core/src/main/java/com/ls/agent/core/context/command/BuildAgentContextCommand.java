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
        List<Long> selectedMcpToolIds
) {
    public BuildAgentContextCommand {
        selectedSkillIds = selectedSkillIds == null ? null : List.copyOf(selectedSkillIds);
        selectedMcpToolIds = selectedMcpToolIds == null ? null : List.copyOf(selectedMcpToolIds);
    }
}
