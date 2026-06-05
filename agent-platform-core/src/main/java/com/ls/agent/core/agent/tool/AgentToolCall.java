package com.ls.agent.core.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;

public record AgentToolCall(
        AgentToolSourceType sourceType,
        String toolName,
        JsonNode arguments
) {
}
