package com.ls.agent.core.model.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.model.api.ModelStreamCallback;
import com.ls.agent.core.model.application.ModelConstants;
import com.ls.agent.core.model.application.ModelValidation;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.model.dto.ModelToolCallDTO;
import com.ls.agent.core.model.dto.ModelToolSpecDTO;
import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;
import com.ls.agent.core.support.security.SecretEncryptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class OpenAiCompatibleProvider implements ModelProvider {

    private final SecretEncryptor secretEncryptor;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleProvider(SecretEncryptor secretEncryptor, ObjectMapper objectMapper) {
        this.secretEncryptor = secretEncryptor;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public boolean supports(ModelConfigEntity config, ModelProviderEntity provider) {
        return ModelConstants.PROVIDER_OPENAI_COMPATIBLE.equals(provider.getProviderType());
    }

    @Override
    public ProviderResponse invoke(ProviderRequest request) {
        return invoke(request, null);
    }

    @Override
    public ProviderResponse invoke(ProviderRequest request, ModelStreamCallback streamCallback) {
        String apiKey = decryptApiKey(request.provider());
        try {
            String requestBody = objectMapper.writeValueAsString(openAiRequestBody(request.config(), request.command()));
            HttpRequest httpRequest = HttpRequest.newBuilder(chatCompletionsUri(request.provider()))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", request.command().stream() ? "text/event-stream" : "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            if (request.command().stream()) {
                HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new BizException(ErrorCode.MODEL_INVOKE_FAILED,
                            "Model provider returned HTTP " + response.statusCode());
                }
                return parseOpenAiStreamResponse(response.body(), streamCallback);
            }
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(ErrorCode.MODEL_INVOKE_FAILED,
                        "Model provider returned HTTP " + response.statusCode());
            }
            return parseOpenAiResponse(response.body());
        } catch (IOException ex) {
            throw new BizException(ErrorCode.MODEL_INVOKE_FAILED, "Model provider response is invalid");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.MODEL_INVOKE_FAILED, "Model invocation was interrupted");
        } catch (RuntimeException ex) {
            if (ex instanceof BizException bizException) {
                throw bizException;
            }
            throw new BizException(ErrorCode.MODEL_INVOKE_FAILED, "Model invocation failed");
        }
    }

    private Map<String, Object> openAiRequestBody(ModelConfigEntity config, ModelInvokeCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModelName());
        body.put("messages", openAiMessages(command.messages()));
        body.put("temperature", effectiveTemperature(config, command));
        body.put("stream", command.stream());
        // 工具通过 System Prompt 中 @skill:xxx / @mcp:xxx 文本格式传递
        // 不发送 OpenAI function calling tools，避免不兼容模型返回空内容
        return body;
    }

    private List<Map<String, Object>> openAiTools(List<ModelToolSpecDTO> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ModelToolSpecDTO tool : tools) {
            if (tool == null || tool.name() == null || tool.name().isBlank()) {
                continue;
            }
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", encodeFunctionName(tool.sourceType(), tool.name()));
            function.put("description", tool.description() == null ? "" : tool.description());
            function.put("parameters", tool.parameterSchema() == null
                    ? Map.of("type", "object", "properties", Map.of())
                    : tool.parameterSchema());
            result.add(Map.of(
                    "type", "function",
                    "function", function
            ));
        }
        return result;
    }

    private List<Map<String, String>> openAiMessages(List<ModelMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of(Map.of("role", "user", "content", ""));
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (ModelMessage message : messages) {
            if (message == null) {
                continue;
            }
            String role = message.role() == null || message.role().isBlank() ? "user" : message.role();
            String content = message.content() == null ? "" : message.content();
            if ("tool".equals(role)) {
                role = "assistant";
                content = "Tool observation:\n" + content;
            }
            result.add(Map.of("role", role, "content", content));
        }
        return result.isEmpty() ? List.of(Map.of("role", "user", "content", "")) : result;
    }

    private BigDecimal effectiveTemperature(ModelConfigEntity config, ModelInvokeCommand command) {
        if (command.temperature() != null) {
            return command.temperature();
        }
        return config.getDefaultTemperature() == null ? BigDecimal.valueOf(0.7) : config.getDefaultTemperature();
    }

    private URI chatCompletionsUri(ModelProviderEntity provider) {
        String baseUrl = ModelValidation.normalizeRequired(provider.getBaseUrl(), "baseUrl");
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalized + "/chat/completions");
    }

    private String decryptApiKey(ModelProviderEntity provider) {
        String apiKey = secretEncryptor.decrypt(provider.getApiKeyEncrypted());
        if (apiKey == null || apiKey.isBlank()) {
            throw new BizException(ErrorCode.MODEL_INVOKE_FAILED, "Model provider API key is missing");
        }
        return apiKey;
    }

    private ProviderResponse parseOpenAiResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode messageNode = root.path("choices").path(0).path("message");
        JsonNode contentNode = messageNode.path("content");
        List<ModelToolCallDTO> toolCalls = parseToolCalls(messageNode.path("tool_calls"));
        if ((contentNode.isMissingNode() || contentNode.isNull() || contentNode.asText().isBlank())
                && toolCalls.isEmpty()) {
            throw new BizException(ErrorCode.MODEL_INVOKE_FAILED, "Model response message is missing");
        }
        String content = contentNode.isMissingNode() || contentNode.isNull() ? "" : contentNode.asText();
        JsonNode usage = root.path("usage");
        int promptTokens = usage.path("prompt_tokens").asInt(0);
        int completionTokens = usage.path("completion_tokens").asInt(0);
        int totalTokens = usage.path("total_tokens").asInt(promptTokens + completionTokens);
        boolean estimated = usage.isMissingNode() || totalTokens == 0;
        if (estimated) {
            promptTokens = UsageParser.estimateTokens(responseBody);
            completionTokens = UsageParser.estimateTokens(content);
            totalTokens = promptTokens + completionTokens;
        }
        return new ProviderResponse(
                content,
                new ModelUsageDTO(promptTokens, completionTokens, totalTokens, estimated),
                toolCalls
        );
    }

    private List<ModelToolCallDTO> parseToolCalls(JsonNode toolCallsNode) throws IOException {
        if (toolCallsNode == null || !toolCallsNode.isArray() || toolCallsNode.isEmpty()) {
            return List.of();
        }
        List<ModelToolCallDTO> result = new ArrayList<>();
        for (JsonNode callNode : toolCallsNode) {
            JsonNode functionNode = callNode.path("function");
            DecodedFunctionName name = decodeFunctionName(functionNode.path("name").asText(""));
            if (name == null) {
                continue;
            }
            String argumentsText = functionNode.path("arguments").asText("{}");
            JsonNode arguments = argumentsText == null || argumentsText.isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(argumentsText);
            result.add(new ModelToolCallDTO(name.sourceType(), name.name(), arguments));
        }
        return result;
    }

    private ProviderResponse parseOpenAiStreamResponse(InputStream responseBody, ModelStreamCallback streamCallback)
            throws IOException {
        StringBuilder assistantMessage = new StringBuilder();
        ModelUsageDTO usage = null;
        StringBuilder rawStream = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rawStream.append(line).append('\n');
                String trimmed = line.strip();
                if (trimmed.isEmpty() || !trimmed.startsWith("data:")) {
                    continue;
                }
                String payload = trimmed.substring("data:".length()).strip();
                if ("[DONE]".equals(payload)) {
                    break;
                }
                JsonNode root = objectMapper.readTree(payload);
                JsonNode delta = root.path("choices").path(0).path("delta").path("content");
                if (delta.isTextual() && !delta.asText().isEmpty()) {
                    String token = delta.asText();
                    assistantMessage.append(token);
                    if (streamCallback != null) {
                        streamCallback.onToken(token);
                    }
                }
                JsonNode usageNode = root.path("usage");
                if (!usageNode.isMissingNode()) {
                    usage = parseUsage(usageNode, rawStream.toString(), assistantMessage.toString());
                }
            }
        }
        String content = assistantMessage.toString();
        if (content.isBlank()) {
            throw new BizException(ErrorCode.MODEL_INVOKE_FAILED, "Model response message is missing");
        }
        if (usage == null) {
            int promptTokens = UsageParser.estimateTokens(rawStream.toString());
            int completionTokens = UsageParser.estimateTokens(content);
            usage = new ModelUsageDTO(promptTokens, completionTokens, promptTokens + completionTokens, true);
        }
        return new ProviderResponse(content, usage);
    }

    private String encodeFunctionName(String sourceType, String name) {
        String source = sourceType == null ? "" : sourceType.toLowerCase();
        return source + "__" + encodeToolName(name);
    }

    private DecodedFunctionName decodeFunctionName(String encodedName) {
        if (encodedName == null || encodedName.isBlank()) {
            return null;
        }
        int separator = encodedName.indexOf("__");
        if (separator <= 0 || separator >= encodedName.length() - 2) {
            return null;
        }
        String sourceType = encodedName.substring(0, separator).toUpperCase();
        String name = decodeToolName(encodedName.substring(separator + 2));
        if (!"SKILL".equals(sourceType) && !"MCP".equals(sourceType)) {
            return null;
        }
        return new DecodedFunctionName(sourceType, name);
    }

    private String encodeToolName(String name) {
        String value = name == null ? "" : name;
        if (value.matches("[A-Za-z0-9_-]+")) {
            return value;
        }
        return "b64_" + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeToolName(String encodedName) {
        if (encodedName == null || !encodedName.startsWith("b64_")) {
            return encodedName;
        }
        byte[] decoded = Base64.getUrlDecoder().decode(encodedName.substring("b64_".length()));
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private record DecodedFunctionName(String sourceType, String name) {
    }

    private ModelUsageDTO parseUsage(JsonNode usage, String responseBody, String content) {
        int promptTokens = usage.path("prompt_tokens").asInt(0);
        int completionTokens = usage.path("completion_tokens").asInt(0);
        int totalTokens = usage.path("total_tokens").asInt(promptTokens + completionTokens);
        boolean estimated = usage.isMissingNode() || totalTokens == 0;
        if (estimated) {
            promptTokens = UsageParser.estimateTokens(responseBody);
            completionTokens = UsageParser.estimateTokens(content);
            totalTokens = promptTokens + completionTokens;
        }
        return new ModelUsageDTO(promptTokens, completionTokens, totalTokens, estimated);
    }
}
