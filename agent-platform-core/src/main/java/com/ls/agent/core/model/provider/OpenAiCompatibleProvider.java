package com.ls.agent.core.model.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.model.application.ModelConstants;
import com.ls.agent.core.model.application.ModelValidation;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;
import com.ls.agent.core.support.security.SecretEncryptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        String apiKey = decryptApiKey(request.provider());
        try {
            String requestBody = objectMapper.writeValueAsString(openAiRequestBody(request.config(), request.command()));
            HttpRequest httpRequest = HttpRequest.newBuilder(chatCompletionsUri(request.provider()))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
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
        body.put("stream", false);
        return body;
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
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
            throw new BizException(ErrorCode.MODEL_INVOKE_FAILED, "Model response message is missing");
        }
        JsonNode usage = root.path("usage");
        int promptTokens = usage.path("prompt_tokens").asInt(0);
        int completionTokens = usage.path("completion_tokens").asInt(0);
        int totalTokens = usage.path("total_tokens").asInt(promptTokens + completionTokens);
        boolean estimated = usage.isMissingNode() || totalTokens == 0;
        if (estimated) {
            promptTokens = UsageParser.estimateTokens(responseBody);
            completionTokens = UsageParser.estimateTokens(contentNode.asText());
            totalTokens = promptTokens + completionTokens;
        }
        return new ProviderResponse(
                contentNode.asText(),
                new ModelUsageDTO(promptTokens, completionTokens, totalTokens, estimated)
        );
    }
}
