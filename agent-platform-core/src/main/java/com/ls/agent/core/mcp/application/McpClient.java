package com.ls.agent.core.mcp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.ls.agent.core.mcp.entity.McpServerEntity;

public interface McpClient {

    JsonNode callTool(McpServerEntity server, String toolName, JsonNode arguments);
}
