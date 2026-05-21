package com.ls.agent.core.mcp.command;

import com.fasterxml.jackson.databind.JsonNode;

public record McpToolExecuteCommand(
        Long tenantId,
        Long userId,
        String toolName,
        JsonNode arguments
) {
}
