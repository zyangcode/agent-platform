package com.ls.agent.core.context.command;

public record BuildAgentContextCommand(
        Long tenantId,
        Long userId,
        Long applicationId,
        Long profileId,
        Long conversationId,
        String userInput,
        Integer maxContextTokens
) {
}
