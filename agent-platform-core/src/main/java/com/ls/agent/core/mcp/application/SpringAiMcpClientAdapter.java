package com.ls.agent.core.mcp.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.mcp.entity.McpServerEntity;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SpringAiMcpClientAdapter implements McpClient {

    private final ObjectMapper objectMapper;
    private final SpringAiMcpSyncClientFactory clientFactory;

    public SpringAiMcpClientAdapter(ObjectMapper objectMapper, SpringAiMcpSyncClientFactory clientFactory) {
        this.objectMapper = objectMapper;
        this.clientFactory = clientFactory;
    }

    public JsonNode listTools(McpServerEntity server) {
        try (McpSyncClient client = clientFactory.create(server)) {
            client.initialize();
            McpSchema.ListToolsResult result = client.listTools();
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode tools = root.putArray("tools");
            if (result != null && result.tools() != null) {
                for (McpSchema.Tool tool : result.tools()) {
                    ObjectNode toolNode = tools.addObject();
                    toolNode.put("name", tool.name());
                    toolNode.put("description", tool.description() == null ? "" : tool.description());
                    toolNode.set("inputSchema", tool.inputSchema() == null
                            ? objectMapper.createObjectNode().put("type", "object")
                            : objectMapper.valueToTree(tool.inputSchema()));
                }
            }
            return root;
        }
    }

    @Override
    public JsonNode callTool(McpServerEntity server, String toolName, JsonNode arguments) {
        try (McpSyncClient client = clientFactory.create(server)) {
            client.initialize();
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    toolName,
                    arguments(arguments)
            ));
            if (result != null && Boolean.TRUE.equals(result.isError())) {
                throw new BizException(ErrorCode.MCP_TOOL_FAILED, firstText(result.content(), "MCP tool failed"));
            }
            if (result != null && result.structuredContent() != null) {
                return objectMapper.valueToTree(result.structuredContent());
            }
            String text = result == null ? "" : firstText(result.content(), "");
            if (!text.isBlank()) {
                return objectMapper.getNodeFactory().textNode(text);
            }
            return objectMapper.valueToTree(result);
        }
    }

    public static boolean supportsServerType(String serverType) {
        return "STREAMABLE_HTTP".equalsIgnoreCase(serverType) || "SSE".equalsIgnoreCase(serverType);
    }

    private Map<String, Object> arguments(JsonNode arguments) {
        if (arguments == null || arguments.isNull() || !arguments.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(arguments, new TypeReference<>() {
        });
    }

    private String firstText(List<McpSchema.Content> content, String defaultValue) {
        if (content == null) {
            return defaultValue;
        }
        for (McpSchema.Content item : content) {
            if (item instanceof McpSchema.TextContent textContent && textContent.text() != null) {
                return textContent.text();
            }
        }
        return defaultValue;
    }
}
