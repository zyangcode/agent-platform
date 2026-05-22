package com.ls.agent.web.dto;

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
        Boolean stream
) {
    public ChatStreamRequest {
        enabledSkillIds = enabledSkillIds == null ? null : List.copyOf(enabledSkillIds);
        enabledMcpToolIds = enabledMcpToolIds == null ? null : List.copyOf(enabledMcpToolIds);
        messages = messages == null ? null : List.copyOf(messages);
    }
}
