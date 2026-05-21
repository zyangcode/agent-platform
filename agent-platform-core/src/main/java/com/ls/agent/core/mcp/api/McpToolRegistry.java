package com.ls.agent.core.mcp.api;

import com.ls.agent.core.mcp.dto.McpToolDTO;

import java.util.List;

public interface McpToolRegistry {

    List<McpToolDTO> listAvailableTools(Long tenantId, List<Long> mcpToolIds);
}
