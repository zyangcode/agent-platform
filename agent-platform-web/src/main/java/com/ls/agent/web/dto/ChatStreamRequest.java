package com.ls.agent.web.dto;

import com.ls.agent.core.agent.command.PendingToolCallCommand;
import com.ls.agent.core.model.dto.ModelMessage;

import java.util.List;

public record ChatStreamRequest(
        Long applicationId,
        String agentMode,
        Long profileId,
        Long conversationId,
        String message,
        List<Long> enabledSkillIds,
        List<Long> enabledMcpToolIds,
        String clientRequestId,
        Long modelConfigId,
        List<ModelMessage> messages,
        Boolean stream,
        List<String> confirmedToolKeys,
        PendingToolCallCommand pendingToolCall
) {
    public ChatStreamRequest {
        enabledSkillIds = enabledSkillIds == null ? null : List.copyOf(enabledSkillIds);
        enabledMcpToolIds = enabledMcpToolIds == null ? null : List.copyOf(enabledMcpToolIds);
        messages = messages == null ? null : List.copyOf(messages);
        confirmedToolKeys = confirmedToolKeys == null ? List.of() : List.copyOf(confirmedToolKeys);
    }

    public ChatStreamRequest(
            Long applicationId,
            String agentMode,
            Long profileId,
            Long conversationId,
            String message,
            List<Long> enabledSkillIds,
            List<Long> enabledMcpToolIds,
            String clientRequestId,
            Long modelConfigId,
            List<ModelMessage> messages,
            Boolean stream,
            List<String> confirmedToolKeys
    ) {
        this(
                applicationId,
                agentMode,
                profileId,
                conversationId,
                message,
                enabledSkillIds,
                enabledMcpToolIds,
                clientRequestId,
                modelConfigId,
                messages,
                stream,
                confirmedToolKeys,
                null
        );
    }
}
