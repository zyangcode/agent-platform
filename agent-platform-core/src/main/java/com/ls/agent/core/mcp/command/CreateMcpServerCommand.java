package com.ls.agent.core.mcp.command;

import com.fasterxml.jackson.databind.JsonNode;

public record CreateMcpServerCommand(
        Long tenantId,
        String name,
        String serverType,
        JsonNode connectionConfig
) {
}
