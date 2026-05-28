package com.ls.agent.core.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;

public record AgentToolDispatchResult(
        boolean success,
        String toolName,
        AgentToolSourceType sourceType,
        JsonNode output,
        String errorMessage
) {
}
