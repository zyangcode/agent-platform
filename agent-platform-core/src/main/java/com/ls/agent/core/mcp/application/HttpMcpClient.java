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
    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";

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
        if (!supportsServerType(server.getServerType())) {
            return objectMapper.createObjectNode();
        }
        try {
            String requestUrl = resolveRequestUrl(server);
            JsonRpcExchange initResult = sendJsonRpc(requestUrl, 1, "initialize", initializeParams(), null);
            if (initResult.result().path("protocolVersion").asText("").isBlank()) return objectMapper.createObjectNode();
            return sendJsonRpc(requestUrl, 2, "tools/list", objectMapper.createObjectNode(), initResult.sessionId()).result();
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    @Override
    public JsonNode callTool(McpServerEntity server, String toolName, JsonNode arguments) {
        if (!supportsServerType(server.getServerType())) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, "Unsupported MCP server type: " + server.getServerType());
        }
        String requestUrl = resolveRequestUrl(server);
        try {
            JsonRpcExchange initResult = sendJsonRpc(requestUrl, 1, "initialize", initializeParams(), null);
            String protocolVersion = initResult.result().path("protocolVersion").asText("");
            if (protocolVersion.isBlank()) {
                throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP HTTP initialize returned no protocol version");
            }
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            params.set("arguments", arguments == null ? objectMapper.createObjectNode() : arguments);
            JsonNode callResult = sendJsonRpc(requestUrl, 2, "tools/call", params, initResult.sessionId()).result();
            JsonNode content = callResult.path("content");
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

    private JsonRpcExchange sendJsonRpc(String requestUrl, int id, String method, JsonNode params, String sessionId)
            throws IOException, InterruptedException {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.set("params", params);
        String body = objectMapper.writeValueAsString(request);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(requestUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (sessionId != null && !sessionId.isBlank()) {
            requestBuilder.header("Mcp-Session-Id", sessionId);
        }
        HttpRequest httpRequest = requestBuilder.build();
        HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED,
                    "MCP HTTP server returned " + response.statusCode());
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String responseSessionId = response.headers().firstValue("mcp-session-id").orElse(sessionId);
        JsonNode jsonRpcResponse;
        try (InputStream bodyStream = response.body()) {
            if (contentType.contains("text/event-stream")) {
                jsonRpcResponse = readSseResponse(bodyStream, id);
            } else {
                jsonRpcResponse = objectMapper.readTree(bodyStream);
            }
        }
        JsonNode error = jsonRpcResponse.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, error.path("message").asText("MCP HTTP request failed"));
        }
        return new JsonRpcExchange(jsonRpcResponse.path("result"), responseSessionId);
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

    private boolean supportsServerType(String serverType) {
        return "HTTP".equalsIgnoreCase(serverType) || "STREAMABLE_HTTP".equalsIgnoreCase(serverType);
    }

    private ObjectNode initializeParams() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", MCP_PROTOCOL_VERSION);
        params.set("capabilities", objectMapper.createObjectNode());
        params.set("clientInfo", objectMapper.createObjectNode()
                .put("name", "agent-platform")
                .put("version", "dev"));
        return params;
    }

    private String resolveRequestUrl(McpServerEntity server) {
        if ("STREAMABLE_HTTP".equalsIgnoreCase(server.getServerType())) {
            return resolveEndpointUrl(server.getConnectionConfig());
        }
        return resolveBaseUrl(server.getConnectionConfig());
    }

    private String resolveEndpointUrl(JsonNode config) {
        String baseUrl = resolveBaseUrl(config);
        String endpoint = config == null || config.path("endpoint").asText("").isBlank()
                ? "/mcp"
                : config.path("endpoint").asText().strip();
        return URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/")
                .resolve(endpoint.startsWith("/") ? endpoint.substring(1) : endpoint)
                .toString();
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

    private record JsonRpcExchange(JsonNode result, String sessionId) {
    }
}
