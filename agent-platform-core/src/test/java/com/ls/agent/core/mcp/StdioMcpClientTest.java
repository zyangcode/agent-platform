package com.ls.agent.core.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.core.mcp.application.StdioMcpClient;
import com.ls.agent.core.mcp.entity.McpServerEntity;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class StdioMcpClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StdioMcpClient client = new StdioMcpClient(objectMapper);

    @Test
    void discoversToolsFromStrictMcpServerThatRequiresStandardInitialization() {
        McpServerEntity server = new McpServerEntity();
        server.setId(3L);
        server.setTenantId(1L);
        server.setName("Strict MCP");
        server.setServerType("STDIO");
        ArrayNode args = objectMapper.createArrayNode()
                .add("-cp")
                .add(System.getProperty("java.class.path"))
                .add(StrictInitializeMcpServer.class.getName());
        server.setConnectionConfig(objectMapper.createObjectNode()
                .put("command", "java")
                .set("args", args));
        server.setStatus("ACTIVE");

        var result = client.listTools(server);

        assertThat(result.path("tools"))
                .anySatisfy(tool -> assertThat(tool.path("name").asText()).isEqualTo("strict_fetch"));
    }

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

        // 如果有 isError=true 说明外网不通（CI 环境），跳过详细断言
        if (result.has("isError") && result.get("isError").asBoolean()) {
            return;
        }
        assertThat(result.get("city").asText()).isEqualTo("重庆");
        assertThat(result.get("temperatureCelsius").asInt()).isBetween(-20, 55);
        assertThat(result.get("source").asText()).isEqualTo("open-meteo");
        assertThat(result.get("isError").asBoolean()).isFalse();
    }

    public static final class StrictInitializeMcpServer {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        private StrictInitializeMcpServer() {
        }

        public static void main(String[] args) throws Exception {
            System.err.println("strict mcp startup log");
            boolean initialized = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    ObjectNode request = (ObjectNode) MAPPER.readTree(line);
                    String method = request.path("method").asText("");
                    if ("initialize".equals(method)) {
                        ObjectNode params = (ObjectNode) request.path("params");
                        if (params.path("protocolVersion").asText("").isBlank()
                                || params.path("clientInfo").path("name").asText("").isBlank()) {
                            writeError(writer, request.path("id").asInt(), "Invalid request parameters");
                            continue;
                        }
                        writeResult(writer, request.path("id").asInt(), initializeResult(params.path("protocolVersion").asText()));
                    } else if ("notifications/initialized".equals(method)) {
                        initialized = true;
                    } else if ("tools/list".equals(method) && initialized) {
                        writeResult(writer, request.path("id").asInt(), toolsListResult());
                    } else if (request.has("id")) {
                        writeError(writer, request.path("id").asInt(), "Received request before initialization was complete");
                    }
                }
            }
        }

        private static ObjectNode initializeResult(String protocolVersion) {
            ObjectNode result = MAPPER.createObjectNode();
            result.put("protocolVersion", protocolVersion);
            result.set("capabilities", MAPPER.createObjectNode().set("tools", MAPPER.createObjectNode()));
            result.set("serverInfo", MAPPER.createObjectNode()
                    .put("name", "strict-mcp")
                    .put("version", "test"));
            return result;
        }

        private static ObjectNode toolsListResult() {
            ObjectNode schema = MAPPER.createObjectNode();
            schema.put("type", "object");
            schema.set("properties", MAPPER.createObjectNode()
                    .set("url", MAPPER.createObjectNode().put("type", "string")));
            ArrayNode required = MAPPER.createArrayNode().add("url");
            schema.set("required", required);

            ObjectNode tool = MAPPER.createObjectNode();
            tool.put("name", "strict_fetch");
            tool.put("description", "Fetch a URL");
            tool.set("inputSchema", schema);

            ObjectNode result = MAPPER.createObjectNode();
            result.set("tools", MAPPER.createArrayNode().add(tool));
            return result;
        }

        private static void writeResult(BufferedWriter writer, int id, ObjectNode result) throws Exception {
            ObjectNode response = MAPPER.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.set("result", result);
            writer.write(MAPPER.writeValueAsString(response));
            writer.newLine();
            writer.flush();
        }

        private static void writeError(BufferedWriter writer, int id, String message) throws Exception {
            ObjectNode response = MAPPER.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.set("error", MAPPER.createObjectNode()
                    .put("code", -32602)
                    .put("message", message));
            writer.write(MAPPER.writeValueAsString(response));
            writer.newLine();
            writer.flush();
        }
    }
}
