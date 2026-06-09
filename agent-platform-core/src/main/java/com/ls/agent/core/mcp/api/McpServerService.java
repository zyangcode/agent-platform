package com.ls.agent.core.mcp.api;

import com.ls.agent.core.mcp.command.CreateMcpServerCommand;
import com.ls.agent.core.mcp.dto.McpServerDTO;

import java.util.List;

public interface McpServerService {

    McpServerDTO create(CreateMcpServerCommand command);

    List<McpServerDTO> list(Long tenantId, String status);

    McpServerDTO disable(Long tenantId, Long mcpServerId);

    McpServerDTO refreshTools(Long tenantId, Long mcpServerId);
}
