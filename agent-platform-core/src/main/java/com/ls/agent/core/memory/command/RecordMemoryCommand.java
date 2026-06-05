package com.ls.agent.core.memory.command;

import java.util.List;

public record RecordMemoryCommand(
        Long tenantId,
        Long userId,
        Long applicationId,
        Long profileId,
        String memoryType,
        String content,
        Long sourceConversationId,
        String memoryCategory,
        List<String> tags,
        Double importance,
        String slotHint
) {

    public RecordMemoryCommand(
            Long tenantId,
            Long userId,
            Long applicationId,
            Long profileId,
            String memoryType,
            String content,
            Long sourceConversationId
    ) {
        this(tenantId, userId, applicationId, profileId, memoryType, content, sourceConversationId, null, List.of(), null, null);
    }

    public RecordMemoryCommand {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
