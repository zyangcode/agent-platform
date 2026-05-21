package com.ls.agent.core.mcp.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record McpToolExecuteResult(
        boolean success,
        String toolName,
        JsonNode output,
        String errorMessage
) {
}
