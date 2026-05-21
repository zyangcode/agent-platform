package com.ls.agent.core.mcp.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record McpToolDTO(
        Long mcpToolId,
        Long mcpServerId,
        String name,
        String description,
        String status,
        JsonNode parameterSchema
) {
}
