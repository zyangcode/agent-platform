package com.ls.agent.core.memory.command;

public record RecordMemoryCommand(
        Long tenantId,
        Long userId,
        Long applicationId,
        Long profileId,
        String memoryType,
        String content,
        Long sourceConversationId
) {
}
