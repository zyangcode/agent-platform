package com.ls.agent.core.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.core.mcp.application.SpringAiMcpClientAdapter;
import com.ls.agent.core.mcp.application.SpringAiMcpSyncClientFactory;
import com.ls.agent.core.mcp.entity.McpServerEntity;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiMcpClientAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpSyncClient client = mock(McpSyncClient.class);
    private final AtomicReference<McpServerEntity> createdFor = new AtomicReference<>();
    private final SpringAiMcpSyncClientFactory factory = server -> {
        createdFor.set(server);
        return client;
    };
    private final SpringAiMcpClientAdapter adapter = new SpringAiMcpClientAdapter(objectMapper, factory);

    @Test
    void listToolsMapsSdkToolsToProjectJsonShape() {
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("query", Map.of("type", "string")),
                List.of("query"),
                true,
                Map.of(),
                Map.of()
        );
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(
                McpSchema.Tool.builder()
                        .name("web.search")
                        .description("Search web")
                        .inputSchema(inputSchema)
                        .build()
        ), null));

        var result = adapter.listTools(server("STREAMABLE_HTTP"));

        assertThat(createdFor.get().getServerType()).isEqualTo("STREAMABLE_HTTP");
        assertThat(result.path("tools")).hasSize(1);
        assertThat(result.path("tools").path(0).path("name").asText()).isEqualTo("web.search");
        assertThat(result.path("tools").path(0).path("description").asText()).isEqualTo("Search web");
        assertThat(result.path("tools").path(0).path("inputSchema").path("type").asText()).isEqualTo("object");
        verify(client).initialize();
        verify(client).close();
    }

    @Test
    void callToolReturnsStructuredContentWhenAvailable() {
        when(client.callTool(org.mockito.ArgumentMatchers.any())).thenReturn(new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("ignored")),
                false,
                Map.of("value", 42),
                Map.of()
        ));

        var result = adapter.callTool(server("SSE"), "calculate", objectMapper.createObjectNode().put("x", 1));

        assertThat(result.path("value").asInt()).isEqualTo(42);
        verify(client).initialize();
        verify(client).close();
    }

    @Test
    void callToolReturnsFirstTextContentWhenStructuredContentIsMissing() {
        when(client.callTool(org.mockito.ArgumentMatchers.any())).thenReturn(new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("plain result")),
                false
        ));

        var result = adapter.callTool(server("STREAMABLE_HTTP"), "echo", objectMapper.createObjectNode());

        assertThat(result.isTextual()).isTrue();
        assertThat(result.asText()).isEqualTo("plain result");
    }

    @Test
    void callToolRejectsMcpErrorResult() {
        when(client.callTool(org.mockito.ArgumentMatchers.any())).thenReturn(new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("tool failed")),
                true
        ));

        assertThatThrownBy(() -> adapter.callTool(server("STREAMABLE_HTTP"), "echo", objectMapper.createObjectNode()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("tool failed");
    }

    private McpServerEntity server(String serverType) {
        McpServerEntity server = new McpServerEntity();
        server.setId(10L);
        server.setTenantId(1L);
        server.setName("Remote MCP");
        server.setServerType(serverType);
        server.setConnectionConfig(objectMapper.createObjectNode()
                .put("baseUrl", "https://mcp.example.test")
                .put("endpoint", "/mcp"));
        server.setStatus("ACTIVE");
        return server;
    }
}
