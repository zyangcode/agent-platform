package com.ls.agent.core.mcp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public final class DemoFilesystemMcpServer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DemoFilesystemMcpServer() {
    }

    public static void main(String[] args) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode request = OBJECT_MAPPER.readTree(line);
                ObjectNode response = OBJECT_MAPPER.createObjectNode();
                response.put("jsonrpc", "2.0");
                response.set("id", request.path("id"));
                String method = request.path("method").asText("");
                if ("initialize".equals(method)) {
                    response.set("result", initializeResult());
                } else if ("tools/call".equals(method)) {
                    response.set("result", callToolResult(request.path("params")));
                } else {
                    ObjectNode error = OBJECT_MAPPER.createObjectNode();
                    error.put("code", -32601);
                    error.put("message", "Unsupported method: " + method);
                    response.set("error", error);
                }
                writer.write(OBJECT_MAPPER.writeValueAsString(response));
                writer.newLine();
                writer.flush();
            }
        }
    }

    private static ObjectNode initializeResult() {
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        result.set("capabilities", OBJECT_MAPPER.createObjectNode()
                .set("tools", OBJECT_MAPPER.createObjectNode()));
        result.set("serverInfo", OBJECT_MAPPER.createObjectNode()
                .put("name", "agent-platform-demo-filesystem")
                .put("version", "1.0.0"));
        return result;
    }

    private static ObjectNode callToolResult(JsonNode params) {
        String toolName = params.path("name").asText("");
        if (!"read_file".equals(toolName)) {
            ObjectNode errorResult = OBJECT_MAPPER.createObjectNode();
            errorResult.put("isError", true);
            errorResult.set("content", OBJECT_MAPPER.createArrayNode()
                    .add(OBJECT_MAPPER.createObjectNode()
                            .put("type", "text")
                            .put("text", "Unsupported tool: " + toolName)));
            return errorResult;
        }
        String path = params.path("arguments").path("path").asText("demo.txt");
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        result.put("path", path);
        result.put("readonly", true);
        result.put("content", "Demo MCP readonly content for " + path);
        result.put("isError", false);
        return result;
    }
}
