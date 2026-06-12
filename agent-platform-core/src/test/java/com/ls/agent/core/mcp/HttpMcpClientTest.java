package com.ls.agent.core.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.core.mcp.application.HttpMcpClient;
import com.ls.agent.core.mcp.entity.McpServerEntity;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpMcpClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpMcpClient client = new HttpMcpClient(objectMapper);

    @Test
    void streamableHttpListToolsCarriesSessionIdAndUnwrapsJsonRpcResult() throws Exception {
        AtomicReference<String> toolsListSessionId = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> handleStreamableMcp(exchange, toolsListSessionId));
        server.start();
        try {
            McpServerEntity mcpServer = new McpServerEntity();
            mcpServer.setId(1L);
            mcpServer.setTenantId(1L);
            mcpServer.setName("Remote MCP");
            mcpServer.setServerType("STREAMABLE_HTTP");
            mcpServer.setConnectionConfig(objectMapper.createObjectNode()
                    .put("baseUrl", "http://127.0.0.1:" + server.getAddress().getPort())
                    .put("endpoint", "/mcp"));
            mcpServer.setStatus("ACTIVE");

            var result = client.listTools(mcpServer);

            assertThat(toolsListSessionId.get()).isEqualTo("test-session");
            assertThat(result.path("tools"))
                    .anySatisfy(tool -> assertThat(tool.path("name").asText()).isEqualTo("query-docs"));
        } finally {
            server.stop(0);
        }
    }

    private void handleStreamableMcp(HttpExchange exchange, AtomicReference<String> toolsListSessionId) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        var request = objectMapper.readTree(body);
        String method = request.path("method").asText("");
        int id = request.path("id").asInt();
        if ("initialize".equals(method)) {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("mcp-session-id", "test-session");
            writeSse(exchange, response(id, objectMapper.createObjectNode()
                    .put("protocolVersion", "2024-11-05")
                    .set("capabilities", objectMapper.createObjectNode().set("tools", objectMapper.createObjectNode()))));
            return;
        }
        if ("tools/list".equals(method)) {
            toolsListSessionId.set(exchange.getRequestHeaders().getFirst("Mcp-Session-Id"));
            ObjectNode result = objectMapper.createObjectNode();
            result.set("tools", objectMapper.createArrayNode()
                    .add(objectMapper.createObjectNode()
                            .put("name", "query-docs")
                            .put("description", "Query documentation")
                            .set("inputSchema", objectMapper.createObjectNode().put("type", "object"))));
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            writeSse(exchange, response(id, result));
            return;
        }
        exchange.sendResponseHeaders(404, -1);
    }

    private ObjectNode response(int id, ObjectNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.set("result", result);
        return response;
    }

    private void writeSse(HttpExchange exchange, ObjectNode response) throws IOException {
        byte[] bytes = ("event: message\n"
                + "data: " + objectMapper.writeValueAsString(response) + "\n\n")
                .getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
