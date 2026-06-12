package com.ls.agent.core.mcp.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.mcp.api.McpToolExecutor;
import com.ls.agent.core.mcp.command.McpToolExecuteCommand;
import com.ls.agent.core.mcp.dto.McpToolExecuteResult;
import com.ls.agent.core.mcp.entity.McpServerEntity;
import com.ls.agent.core.mcp.entity.McpToolEntity;
import com.ls.agent.core.mcp.mapper.McpServerMapper;
import com.ls.agent.core.mcp.mapper.McpToolMapper;
import org.springframework.stereotype.Service;

@Service
public class DefaultMcpToolExecutor implements McpToolExecutor {

    private static final String TOOL_STATUS_AVAILABLE = "AVAILABLE";
    private static final String SERVER_STATUS_ACTIVE = "ACTIVE";

    private final ObjectMapper objectMapper;
    private final McpToolMapper mcpToolMapper;
    private final McpServerMapper mcpServerMapper;
    private final StdioMcpClient stdioMcpClient;
    private final HttpMcpClient httpMcpClient;
    private final SpringAiMcpClientAdapter springAiMcpClientAdapter;

    public DefaultMcpToolExecutor(
            ObjectMapper objectMapper,
            McpToolMapper mcpToolMapper,
            McpServerMapper mcpServerMapper,
            StdioMcpClient stdioMcpClient,
            HttpMcpClient httpMcpClient,
            SpringAiMcpClientAdapter springAiMcpClientAdapter
    ) {
        this.objectMapper = objectMapper;
        this.mcpToolMapper = mcpToolMapper;
        this.mcpServerMapper = mcpServerMapper;
        this.stdioMcpClient = stdioMcpClient;
        this.httpMcpClient = httpMcpClient;
        this.springAiMcpClientAdapter = springAiMcpClientAdapter;
    }

    @Override
    public McpToolExecuteResult execute(McpToolExecuteCommand command) {
        validate(command);
        McpToolEntity tool = resolveTool(command);
        McpServerEntity server = resolveServer(command, tool);
        try {
            McpClient client = resolveClient(server);
            return new McpToolExecuteResult(
                    true,
                    command.toolName(),
                    client.callTool(server, command.toolName(), command.arguments()),
                    null
            );
        } catch (Exception ex) {
            return new McpToolExecuteResult(
                    false,
                    command.toolName(),
                    objectMapper.createObjectNode().put("error", safeMessage(ex)),
                    safeMessage(ex)
            );
        }
    }

    private McpToolEntity resolveTool(McpToolExecuteCommand command) {
        var tools = mcpToolMapper.selectList(new LambdaQueryWrapper<McpToolEntity>()
                .eq(McpToolEntity::getName, command.toolName())
                .eq(McpToolEntity::getStatus, TOOL_STATUS_AVAILABLE));
        if (tools == null || tools.isEmpty()) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP tool is unavailable: " + command.toolName());
        }
        for (McpToolEntity tool : tools) {
            if (findServer(command, tool) != null) {
                return tool;
            }
        }
        throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP tool is unavailable: " + command.toolName());
    }

    private McpServerEntity resolveServer(McpToolExecuteCommand command, McpToolEntity tool) {
        McpServerEntity server = findServer(command, tool);
        if (server == null) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP server is unavailable for tool: " + command.toolName());
        }
        return server;
    }

    private McpServerEntity findServer(McpToolExecuteCommand command, McpToolEntity tool) {
        var servers = mcpServerMapper.selectList(new LambdaQueryWrapper<McpServerEntity>()
                .eq(McpServerEntity::getId, tool.getMcpServerId())
                .eq(McpServerEntity::getTenantId, command.tenantId())
                .eq(McpServerEntity::getStatus, SERVER_STATUS_ACTIVE));
        if (servers == null || servers.isEmpty()) {
            return null;
        }
        return servers.get(0);
    }

    private McpClient resolveClient(McpServerEntity server) {
        if ("STDIO".equalsIgnoreCase(server.getServerType())) {
            return stdioMcpClient;
        }
        if ("HTTP".equalsIgnoreCase(server.getServerType()) || "STREAMABLE_HTTP".equalsIgnoreCase(server.getServerType())) {
            return httpMcpClient;
        }
        if (SpringAiMcpClientAdapter.supportsServerType(server.getServerType())) {
            return springAiMcpClientAdapter;
        }
        throw new BizException(ErrorCode.MCP_TOOL_FAILED, "Unsupported MCP server type: " + server.getServerType());
    }

    private void validate(McpToolExecuteCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP tool command is required");
        }
        if (command.tenantId() == null) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP tenantId is required");
        }
        if (command.toolName() == null || command.toolName().isBlank()) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP tool name is required");
        }
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
