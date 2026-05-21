package com.ls.agent.core.mcp.api;

import com.ls.agent.core.mcp.dto.McpToolDTO;

import java.util.List;

public interface McpToolQueryService {

    boolean areMcpToolsBindable(Long tenantId, List<Long> mcpToolIds);

    List<McpToolDTO> listTools(Long tenantId, String status);
}
