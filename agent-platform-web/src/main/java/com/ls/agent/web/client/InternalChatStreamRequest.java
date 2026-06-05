package com.ls.agent.web.client;

import com.ls.agent.core.agent.command.PendingToolCallCommand;
import com.ls.agent.core.model.dto.ModelMessage;

import java.util.List;

public record InternalChatStreamRequest(
        Long tenantId,
        Long applicationId,
        Long userId,
        String channel,
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
    public InternalChatStreamRequest {
        enabledSkillIds = enabledSkillIds == null ? null : List.copyOf(enabledSkillIds);
        enabledMcpToolIds = enabledMcpToolIds == null ? null : List.copyOf(enabledMcpToolIds);
        messages = messages == null ? null : List.copyOf(messages);
        confirmedToolKeys = confirmedToolKeys == null ? List.of() : List.copyOf(confirmedToolKeys);
    }

    public InternalChatStreamRequest(
            Long tenantId,
            Long applicationId,
            Long userId,
            String channel,
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
                tenantId,
                applicationId,
                userId,
                channel,
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
