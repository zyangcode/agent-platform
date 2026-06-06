package com.ls.agent.core.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.mcp.application.DefaultMcpToolExecutor;
import com.ls.agent.core.mcp.application.HttpMcpClient;
import com.ls.agent.core.mcp.application.StdioMcpClient;
import com.ls.agent.core.mcp.command.McpToolExecuteCommand;
import com.ls.agent.core.mcp.dto.McpToolExecuteResult;
import com.ls.agent.core.mcp.entity.McpServerEntity;
import com.ls.agent.core.mcp.entity.McpToolEntity;
import com.ls.agent.core.mcp.mapper.McpServerMapper;
import com.ls.agent.core.mcp.mapper.McpToolMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultMcpToolExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpToolMapper toolMapper = mock(McpToolMapper.class);
    private final McpServerMapper serverMapper = mock(McpServerMapper.class);

    @Test
    void readFileDispatchesThroughResolvedMcpClient() {
        McpServerEntity server = server();
        McpToolEntity tool = tool();
        AtomicReference<McpServerEntity> calledServer = new AtomicReference<>();
        AtomicReference<String> calledToolName = new AtomicReference<>();
        AtomicReference<JsonNode> calledArguments = new AtomicReference<>();
        StdioMcpClient stdioClient = mock(StdioMcpClient.class);
        HttpMcpClient httpClient = mock(HttpMcpClient.class);
        when(stdioClient.callTool(any(), any(), any())).thenAnswer(invocation -> {
            calledServer.set(invocation.getArgument(0));
            calledToolName.set(invocation.getArgument(1));
            calledArguments.set(invocation.getArgument(2));
            return objectMapper.createObjectNode()
                    .put("path", invocation.<JsonNode>getArgument(2).get("path").asText())
                    .put("content", "real mcp content");
        });
        DefaultMcpToolExecutor executor = new DefaultMcpToolExecutor(objectMapper, toolMapper, serverMapper, stdioClient, httpClient);
        when(serverMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(server));
        when(toolMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(tool));

        McpToolExecuteResult result = executor.execute(new McpToolExecuteCommand(
                1L,
                10001L,
                "read_file",
                objectMapper.createObjectNode().put("path", "demo.txt")
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.output().get("content").asText()).isEqualTo("real mcp content");
        assertThat(calledServer.get()).isSameAs(server);
        assertThat(calledToolName.get()).isEqualTo("read_file");
        assertThat(calledArguments.get().get("path").asText()).isEqualTo("demo.txt");
    }

    private McpServerEntity server() {
        McpServerEntity server = new McpServerEntity();
        server.setId(10L);
        server.setTenantId(1L);
        server.setName("Readonly Filesystem MCP");
        server.setServerType("STDIO");
        server.setConnectionConfig(objectMapper.createObjectNode().put("command", "mock-filesystem-mcp"));
        server.setStatus("ACTIVE");
        return server;
    }

    private McpToolEntity tool() {
        McpToolEntity tool = new McpToolEntity();
        tool.setId(1L);
        tool.setMcpServerId(10L);
        tool.setName("read_file");
        tool.setDescription("Read a file");
        tool.setStatus("AVAILABLE");
        return tool;
    }
}
