package com.ls.agent.core.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.mcp.application.StdioMcpClient;
import com.ls.agent.core.mcp.entity.McpServerEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StdioMcpClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StdioMcpClient client = new StdioMcpClient(objectMapper);

    @Test
    void callsBundledDemoFilesystemMcpServerOverStdio() {
        McpServerEntity server = new McpServerEntity();
        server.setId(1L);
        server.setTenantId(1L);
        server.setName("Bundled Demo Filesystem MCP");
        server.setServerType("STDIO");
        server.setConnectionConfig(objectMapper.createObjectNode()
                .put("command", "builtin-demo-filesystem-mcp"));
        server.setStatus("ACTIVE");

        var result = client.callTool(
                server,
                "read_file",
                objectMapper.createObjectNode().put("path", "demo.txt")
        );

        assertThat(result.get("content").asText()).contains("Demo MCP readonly content", "demo.txt");
        assertThat(result.get("readonly").asBoolean()).isTrue();
    }

    @Test
    void callsBundledDemoWeatherMcpServerOverStdio() {
        McpServerEntity server = new McpServerEntity();
        server.setId(2L);
        server.setTenantId(1L);
        server.setName("Bundled Demo Weather MCP");
        server.setServerType("STDIO");
        server.setConnectionConfig(objectMapper.createObjectNode()
                .put("command", "builtin-demo-weather-mcp"));
        server.setStatus("ACTIVE");

        var result = client.callTool(
                server,
                "weather.current",
                objectMapper.createObjectNode().put("city", "重庆")
        );

        assertThat(result.get("city").asText()).isEqualTo("重庆");
        assertThat(result.get("summary").asText()).contains("重庆", "篮球");
        assertThat(result.get("temperatureCelsius").asInt()).isBetween(20, 40);
        assertThat(result.get("source").asText()).isEqualTo("demo-mcp-weather");
        assertThat(result.get("isError").asBoolean()).isFalse();
    }
}
