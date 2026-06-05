package com.ls.agent.core.agent.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.ls.agent.core.agent.tool.AgentToolSourceType;

public record PendingToolCallCommand(
        AgentToolSourceType sourceType,
        String toolName,
        JsonNode arguments
) {
}
