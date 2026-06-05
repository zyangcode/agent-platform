package com.ls.agent.core.mcp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.mcp.entity.McpServerEntity;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class StdioMcpClient implements McpClient {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(8);

    private final ObjectMapper objectMapper;

    public StdioMcpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public JsonNode callTool(McpServerEntity server, String toolName, JsonNode arguments) {
        if (!"STDIO".equalsIgnoreCase(server.getServerType())) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, "Unsupported MCP server type: " + server.getServerType());
        }
        List<String> command = stdioCommand(server.getConnectionConfig());
        Process process = null;
        try {
            process = new ProcessBuilder(command).start();
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                writeJsonRpc(writer, 1, "initialize", objectMapper.createObjectNode());
                readJsonRpc(reader, 1);
                ObjectNode params = objectMapper.createObjectNode();
                params.put("name", toolName);
                params.set("arguments", arguments == null ? objectMapper.createObjectNode() : arguments);
                writeJsonRpc(writer, 2, "tools/call", params);
                JsonNode result = readJsonRpc(reader, 2);
                JsonNode error = result.path("error");
                if (!error.isMissingNode() && !error.isNull()) {
                    throw new BizException(ErrorCode.MCP_TOOL_FAILED, error.path("message").asText("MCP tool failed"));
                }
                return result.path("result");
            }
        } catch (IOException ex) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP stdio call failed: " + safeMessage(ex));
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    private List<String> stdioCommand(JsonNode config) {
        if (config == null || config.path("command").asText("").isBlank()) {
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP stdio command is missing");
        }
        String configuredCommand = config.path("command").asText();
        if ("builtin-demo-filesystem-mcp".equals(configuredCommand)) {
            return List.of(
                    javaCommand(),
                    "-cp",
                    System.getProperty("java.class.path"),
                    DemoFilesystemMcpServer.class.getName()
            );
        }
        if ("builtin-demo-weather-mcp".equals(configuredCommand)) {
            return List.of(
                    javaCommand(),
                    "-cp",
                    System.getProperty("java.class.path"),
                    DemoWeatherMcpServer.class.getName()
            );
        }
        List<String> command = new ArrayList<>();
        command.add(configuredCommand);
        JsonNode args = config.path("args");
        if (args.isArray()) {
            for (JsonNode arg : args) {
                String value = arg.asText("");
                if (!value.isBlank()) {
                    command.add(value);
                }
            }
        }
        return command;
    }

    private String javaCommand() {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) {
            return "java";
        }
        return javaHome + java.io.File.separator + "bin" + java.io.File.separator + "java";
    }

    private void writeJsonRpc(BufferedWriter writer, int id, String method, JsonNode params) throws IOException {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.set("params", params);
        writer.write(objectMapper.writeValueAsString(request));
        writer.newLine();
        writer.flush();
    }

    private JsonNode readJsonRpc(BufferedReader reader, int expectedId) throws IOException {
        long deadline = System.nanoTime() + CALL_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (!reader.ready()) {
                sleepBriefly();
                continue;
            }
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            if (line.isBlank()) {
                continue;
            }
            JsonNode response = objectMapper.readTree(line);
            if (response.path("id").asInt(-1) == expectedId) {
                return response;
            }
        }
        throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP stdio response timed out");
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.MCP_TOOL_FAILED, "MCP stdio call interrupted");
        }
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
