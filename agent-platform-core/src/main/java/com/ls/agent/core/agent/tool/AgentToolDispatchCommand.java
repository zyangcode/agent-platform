package com.ls.agent.core.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;

public record AgentToolDispatchCommand(
        Long tenantId,
        Long userId,
        String toolName,
        AgentToolSourceType sourceType,
        JsonNode arguments
) {
}
