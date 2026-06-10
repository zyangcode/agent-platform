package com.ls.agent.core.mcp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.mcp.entity.McpServerEntity;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.springframework.stereotype.Component;

import java.time.Duration;

@FunctionalInterface
public interface SpringAiMcpSyncClientFactory {

    McpSyncClient create(McpServerEntity server);
}

@Component
class DefaultSpringAiMcpSyncClientFactory implements SpringAiMcpSyncClientFactory {

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration DEFAULT_INITIALIZATION_TIMEOUT = Duration.ofSeconds(10);

    @Override
    public McpSyncClient create(McpServerEntity server) {
        McpClientTransport transport = transport(server);
        return io.modelcontextprotocol.client.McpClient.sync(transport)
                .requestTimeout(timeout(server.getConnectionConfig(), "requestTimeoutSeconds", DEFAULT_REQUEST_TIMEOUT))
                .initializationTimeout(timeout(server.getConnectionConfig(), "initializationTimeoutSeconds", DEFAULT_INITIALIZATION_TIMEOUT))
                .build();
    }

    private McpClientTransport transport(McpServerEntity server) {
        String serverType = server.getServerType() == null ? "" : server.getServerType().toUpperCase();
        String baseUrl = requiredHttpUrl(server.getConnectionConfig(), "baseUrl");
        if ("STREAMABLE_HTTP".equals(serverType)) {
            return HttpClientStreamableHttpTransport.builder(baseUrl)
                    .endpoint(text(server.getConnectionConfig(), "endpoint", "/mcp"))
                    .build();
        }
        if ("SSE".equals(serverType)) {
            return HttpClientSseClientTransport.builder(baseUrl)
                    .sseEndpoint(text(server.getConnectionConfig(), "sseEndpoint", "/sse"))
                    .build();
        }
        throw new BizException(ErrorCode.MCP_TOOL_FAILED, "Unsupported Spring AI MCP server type: " + server.getServerType());
    }

    private String requiredHttpUrl(JsonNode config, String field) {
        String value = text(config, field, "");
        if (value.isBlank()) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP " + field + " is missing");
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP " + field + " must start with http or https");
        }
        return value;
    }

    private String text(JsonNode config, String field, String defaultValue) {
        if (config == null || config.path(field).asText("").isBlank()) {
            return defaultValue;
        }
        return config.path(field).asText().strip();
    }

    private Duration timeout(JsonNode config, String field, Duration defaultValue) {
        if (config == null || !config.has(field)) {
            return defaultValue;
        }
        long seconds = config.path(field).asLong(defaultValue.toSeconds());
        return Duration.ofSeconds(Math.max(1, seconds));
    }
}
