package com.ls.agent.core.mcp.api;

import com.ls.agent.core.mcp.command.McpToolExecuteCommand;
import com.ls.agent.core.mcp.dto.McpToolExecuteResult;

public interface McpToolExecutor {

    McpToolExecuteResult execute(McpToolExecuteCommand command);
}
