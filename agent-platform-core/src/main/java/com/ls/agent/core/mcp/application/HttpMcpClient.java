package com.ls.agent.core.mcp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.mcp.entity.McpServerEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class HttpMcpClient implements McpClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public HttpMcpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    HttpMcpClient(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public JsonNode listTools(McpServerEntity server) {
        if (!"HTTP".equalsIgnoreCase(server.getServerType())) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode initResult = sendJsonRpc(resolveBaseUrl(server.getConnectionConfig()), 1, "initialize", objectMapper.createObjectNode());
            if (initResult.path("protocolVersion").asText("").isBlank()) return objectMapper.createObjectNode();
            return sendJsonRpc(resolveBaseUrl(server.getConnectionConfig()), 2, "tools/list", objectMapper.createObjectNode());
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    @Override
    public JsonNode callTool(McpServerEntity server, String toolName, JsonNode arguments) {
        if (!"HTTP".equalsIgnoreCase(server.getServerType())) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, "Unsupported MCP server type: " + server.getServerType());
        }
        String baseUrl = resolveBaseUrl(server.getConnectionConfig());
        try {
            JsonNode initResult = sendJsonRpc(baseUrl, 1, "initialize", objectMapper.createObjectNode());
            String protocolVersion = initResult.path("protocolVersion").asText("");
            if (protocolVersion.isBlank()) {
                throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP HTTP initialize returned no protocol version");
            }
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            params.set("arguments", arguments == null ? objectMapper.createObjectNode() : arguments);
            JsonNode callResult = sendJsonRpc(baseUrl, 2, "tools/call", params);
            JsonNode error = callResult.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                throw new BizException(ErrorCode.MCP_TOOL_FAILED, error.path("message").asText("MCP HTTP tool failed"));
            }
            JsonNode content = callResult.path("result").path("content");
            if (content.isArray()) {
                for (JsonNode item : content) {
                    if ("text".equals(item.path("type").asText("")) && item.has("text")) {
                        return item.path("text");
                    }
                }
            }
            return callResult.path("result");
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP HTTP call failed: " + safeMessage(ex));
        }
    }

    private JsonNode sendJsonRpc(String baseUrl, int id, String method, JsonNode params)
            throws IOException, InterruptedException {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.set("params", params);
        String body = objectMapper.writeValueAsString(request);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED,
                    "MCP HTTP server returned " + response.statusCode());
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        try (InputStream bodyStream = response.body()) {
            if (contentType.contains("text/event-stream")) {
                return readSseResponse(bodyStream, id);
            }
            return objectMapper.readTree(bodyStream);
        }
    }

    private JsonNode readSseResponse(InputStream body, int expectedId) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || !trimmed.startsWith("data:")) {
                continue;
            }
            String payload = trimmed.substring("data:".length()).strip();
            if ("[DONE]".equals(payload)) {
                break;
            }
            JsonNode response = objectMapper.readTree(payload);
            if (response.path("id").asInt(-1) == expectedId) {
                return response;
            }
        }
        throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP HTTP SSE response missing expected id: " + expectedId);
    }

    private String resolveBaseUrl(JsonNode config) {
        if (config == null || config.path("baseUrl").asText("").isBlank()) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP HTTP baseUrl is missing");
        }
        String url = config.path("baseUrl").asText().strip();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP HTTP baseUrl must start with http or https");
        }
        return url;
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
