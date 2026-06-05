package com.ls.agent.core.mcp.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record McpServerDTO(
        Long mcpServerId,
        String name,
        String serverType,
        JsonNode connectionConfig,
        String status
) {
}
