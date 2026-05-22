package com.ls.agent.gateway.dto;

import com.ls.agent.core.model.dto.ModelMessage;

import java.util.List;

public record GatewayChatRequest(
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
    public GatewayChatRequest {
        enabledSkillIds = enabledSkillIds == null ? null : List.copyOf(enabledSkillIds);
        enabledMcpToolIds = enabledMcpToolIds == null ? null : List.copyOf(enabledMcpToolIds);
        messages = messages == null ? null : List.copyOf(messages);
    }
}
