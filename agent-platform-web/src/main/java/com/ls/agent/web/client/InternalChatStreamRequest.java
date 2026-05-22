package com.ls.agent.web.client;

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
        Boolean stream
) {
    public InternalChatStreamRequest {
        enabledSkillIds = enabledSkillIds == null ? null : List.copyOf(enabledSkillIds);
        enabledMcpToolIds = enabledMcpToolIds == null ? null : List.copyOf(enabledMcpToolIds);
        messages = messages == null ? null : List.copyOf(messages);
    }
}
