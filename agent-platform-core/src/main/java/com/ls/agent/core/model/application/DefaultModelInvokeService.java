package com.ls.agent.core.model.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;
import com.ls.agent.core.model.mapper.ModelConfigMapper;
import com.ls.agent.core.model.mapper.ModelProviderMapper;
import com.ls.agent.core.support.security.SecretEncryptor;
import org.springframework.stereotype.Service;

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

@Service
public class DefaultModelInvokeService implements ModelInvokeService {

    private final ModelConfigMapper configMapper;
    private final ModelProviderMapper providerMapper;
    private final SecretEncryptor secretEncryptor;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DefaultModelInvokeService(
            ModelConfigMapper configMapper,
            ModelProviderMapper providerMapper,
            SecretEncryptor secretEncryptor,
            ObjectMapper objectMapper
    ) {
        this.configMapper = configMapper;
        this.providerMapper = providerMapper;
        this.secretEncryptor = secretEncryptor;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ModelInvokeResult invoke(ModelInvokeCommand command) {
        Long modelConfigId = ModelValidation.requireNonNull(command.modelConfigId(), "modelConfigId");
        ModelConfigEntity config = configMapper.selectById(modelConfigId);
        if (config == null || !ModelConstants.STATUS_ACTIVE.equals(config.getStatus())) {
            throw new BizException(ErrorCode.MODEL_INVOKE_FAILED, "Model config is unavailable");
        }
        ModelProviderEntity provider = providerMapper.selectById(config.getProviderId());
        if (provider == null || !ModelConstants.STATUS_ACTIVE.equals(provider.getStatus())) {
            throw new BizException(ErrorCode.MODEL_INVOKE_FAILED, "Model provider is unavailable");
        }
        if (ModelConstants.MODEL_MOCK_CHAT.equals(config.getModelName())) {
            return mockChat(config, command.messages());
        }
        if (ModelConstants.PROVIDER_OPENAI_COMPATIBLE.equals(provider.getProviderType())) {
            return invokeOpenAiCompatible(config, provider, command);
        }
        throw new BizException(ErrorCode.MODEL_INVOKE_FAILED, "Model provider type is unsupported");
    }

    private ModelInvokeResult invokeOpenAiCompatible(
            ModelConfigEntity config,
            ModelProviderEntity provider,
            ModelInvokeCommand command
    ) {
        String apiKey = decryptApiKey(provider);
        try {
            String requestBody = objectMapper.writeValueAsString(openAiRequestBody(config, command));
            HttpRequest request = HttpRequest.newBuilder(chatCompletionsUri(provider))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(ErrorCode.MODEL_INVOKE_FAILED,
                        "Model provider returned HTTP " + response.statusCode());
            }
            return parseOpenAiResponse(config, response.body());
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

    private ModelInvokeResult parseOpenAiResponse(ModelConfigEntity config, String responseBody) throws IOException {
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
            promptTokens = estimateTokens(responseBody);
            completionTokens = estimateTokens(contentNode.asText());
            totalTokens = promptTokens + completionTokens;
        }
        return new ModelInvokeResult(
                config.getId(),
                config.getModelName(),
                contentNode.asText(),
                new ModelUsageDTO(promptTokens, completionTokens, totalTokens, estimated)
        );
    }

    private ModelInvokeResult mockChat(ModelConfigEntity config, List<ModelMessage> messages) {
        String lastUserMessage = messages == null ? "" : messages.stream()
                .filter(message -> "user".equals(message.role()))
                .map(ModelMessage::content)
                .reduce((first, second) -> second)
                .orElse("");
        String assistantMessage = "[mock-chat] " + (lastUserMessage.isBlank()
                ? "Hello, this is a mock model response."
                : "Echo: " + lastUserMessage);
        int promptTokens = estimateTokens(lastUserMessage);
        int completionTokens = estimateTokens(assistantMessage);
        return new ModelInvokeResult(
                config.getId(),
                config.getModelName(),
                assistantMessage,
                new ModelUsageDTO(promptTokens, completionTokens, promptTokens + completionTokens, true)
        );
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (text.length() + 3) / 4);
    }
}
