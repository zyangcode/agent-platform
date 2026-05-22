package com.ls.agent.core.agent.command;

import java.util.List;

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
        Integer maxContextTokens
) {
    public AgentRunCommand {
        selectedSkillIds = selectedSkillIds == null ? null : List.copyOf(selectedSkillIds);
        selectedMcpToolIds = selectedMcpToolIds == null ? null : List.copyOf(selectedMcpToolIds);
    }
}
