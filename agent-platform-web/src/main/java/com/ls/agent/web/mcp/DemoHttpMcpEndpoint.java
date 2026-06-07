package com.ls.agent.web.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@RestController
public class DemoHttpMcpEndpoint {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @PostMapping(value = "/demo/mcp", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ObjectNode handleJsonRpc(@RequestBody JsonNode request) throws IOException, InterruptedException {
        int id = request.path("id").asInt(0);
        String method = request.path("method").asText("");
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        if ("initialize".equals(method)) {
            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            result.put("protocolVersion", "2024-11-05");
            result.set("capabilities", OBJECT_MAPPER.createObjectNode().set("tools", OBJECT_MAPPER.createObjectNode()));
            result.set("serverInfo", OBJECT_MAPPER.createObjectNode().put("name", "demo-http-calculator").put("version", "1.0"));
            response.set("result", result);
            return response;
        }

        if ("tools/list".equals(method)) {
            var tools = OBJECT_MAPPER.createArrayNode();
            tools.add(OBJECT_MAPPER.createObjectNode()
                    .put("name", "calculator")
                    .put("description", "Calculate math expressions")
                    .set("inputSchema", OBJECT_MAPPER.createObjectNode()
                            .put("type", "object")
                            .set("properties", OBJECT_MAPPER.createObjectNode()
                                    .set("expression", OBJECT_MAPPER.createObjectNode()
                                            .put("type", "string")
                                            .put("description", "Math expression")))));
            tools.add(OBJECT_MAPPER.createObjectNode()
                    .put("name", "search")
                    .put("description", "Search Wikipedia")
                    .set("inputSchema", OBJECT_MAPPER.createObjectNode()
                            .put("type", "object")
                            .set("properties", OBJECT_MAPPER.createObjectNode()
                                    .set("query", OBJECT_MAPPER.createObjectNode()
                                            .put("type", "string")
                                            .put("description", "Search query")))));
            response.set("result", OBJECT_MAPPER.createObjectNode().set("tools", tools));
            return response;
        }

        if ("tools/call".equals(method)) {
            String toolName = request.path("params").path("name").asText("");
            JsonNode arguments = request.path("params").path("arguments");
            ObjectNode result = executeTool(toolName, arguments);
            response.set("result", result);
            return response;
        }

        ObjectNode error = OBJECT_MAPPER.createObjectNode();
        error.put("code", -32601);
        error.put("message", "Unsupported method: " + method);
        response.set("error", error);
        return response;
    }

    private ObjectNode executeTool(String toolName, JsonNode args) throws IOException, InterruptedException {
        if ("calculator".equals(toolName)) {
            String expr = args.path("expression").asText("1+1");
            try {
                double value = parseExpression(expr);
                ObjectNode result = OBJECT_MAPPER.createObjectNode();
                result.put("expression", expr);
                result.put("result", value);
                result.set("content", OBJECT_MAPPER.createArrayNode()
                        .add(OBJECT_MAPPER.createObjectNode().put("type", "text").put("text", expr + " = " + value)));
                return result;
            } catch (Exception ex) {
                ObjectNode error = OBJECT_MAPPER.createObjectNode();
                error.put("isError", true);
                error.set("content", OBJECT_MAPPER.createArrayNode()
                        .add(OBJECT_MAPPER.createObjectNode().put("type", "text").put("text", "计算失败: " + ex.getMessage())));
                return error;
            }
        }
        if ("search".equals(toolName)) {
            String query = args.path("query").asText("");
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            URI uri = URI.create("https://en.wikipedia.org/w/api.php?action=opensearch&format=json&limit=3&search=" + encoded);
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<String> resp = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            result.put("query", query);
            result.put("results", resp.body());
            result.set("content", OBJECT_MAPPER.createArrayNode()
                    .add(OBJECT_MAPPER.createObjectNode().put("type", "text").put("text", "Wikipedia search: " + resp.body())));
            return result;
        }
        ObjectNode error = OBJECT_MAPPER.createObjectNode();
        error.put("isError", true);
        error.set("content", OBJECT_MAPPER.createArrayNode()
                .add(OBJECT_MAPPER.createObjectNode().put("type", "text").put("text", "Unknown tool: " + toolName)));
        return error;
    }

    private double parseExpression(String expr) {
        expr = expr.replaceAll("\\s+", "");
        String[] parts = expr.split("(?=[+\\-*/])|(?<=[+\\-*/])");
        double result = Double.parseDouble(parts[0]);
        for (int i = 1; i < parts.length; i += 2) {
            char op = parts[i].charAt(0);
            double num = Double.parseDouble(parts[i + 1]);
            result = switch (op) {
                case '+' -> result + num;
                case '-' -> result - num;
                case '*' -> result * num;
                case '/' -> result / num;
                default -> result;
            };
        }
        return result;
    }
}
