package com.ls.agent.core.mcp.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.mcp.api.McpToolExecutor;
import com.ls.agent.core.mcp.command.McpToolExecuteCommand;
import com.ls.agent.core.mcp.dto.McpToolExecuteResult;
import org.springframework.stereotype.Service;

@Service
public class DefaultMcpToolExecutor implements McpToolExecutor {

    private final ObjectMapper objectMapper;

    public DefaultMcpToolExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public McpToolExecuteResult execute(McpToolExecuteCommand command) {
        if ("read_file".equals(command.toolName())) {
            return readFile(command);
        }
        throw new BizException(ErrorCode.MCP_TOOL_FAILED, "Unsupported MCP tool: " + command.toolName());
    }

    private McpToolExecuteResult readFile(McpToolExecuteCommand command) {
        String path = requiredText(command, "path");
        ObjectNode output = objectMapper.createObjectNode();
        output.put("path", path);
        output.put("readonly", true);
        output.put("content", "Mock readonly file content for " + path);
        return new McpToolExecuteResult(true, command.toolName(), output, null);
    }

    private String requiredText(McpToolExecuteCommand command, String field) {
        if (command.arguments() == null || command.arguments().get(field) == null
                || command.arguments().get(field).asText().isBlank()) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, "Missing MCP tool argument: " + field);
        }
        return command.arguments().get(field).asText();
    }
}
