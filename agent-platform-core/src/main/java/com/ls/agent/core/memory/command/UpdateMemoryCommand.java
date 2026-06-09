package com.ls.agent.core.memory.command;

import java.util.List;

public record UpdateMemoryCommand(
        Long tenantId,
        Long applicationId,
        Long userId,
        Long profileId,
        Long memoryId,
        String content,
        String memoryCategory,
        List<String> tags,
        Double importance,
        String slotHint,
        Boolean pinned
) {
}
