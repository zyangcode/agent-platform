package com.ls.agent.core.model.provider;

import com.ls.agent.core.model.application.ModelValidation;
import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DefaultSpringAiChatModelFactory implements SpringAiChatModelFactory {

    @Override
    public ChatModel create(ModelConfigEntity config, ModelProviderEntity provider, String apiKey) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(normalizeBaseUrl(provider))
                .apiKey(apiKey)
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.getModelName())
                .temperature(effectiveTemperature(config).doubleValue())
                .streamUsage(true)
                .internalToolExecutionEnabled(false)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    private String normalizeBaseUrl(ModelProviderEntity provider) {
        String baseUrl = ModelValidation.normalizeRequired(provider.getBaseUrl(), "baseUrl");
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/chat/completions")) {
            return normalized.substring(0, normalized.length() - "/chat/completions".length());
        }
        return normalized;
    }

    private BigDecimal effectiveTemperature(ModelConfigEntity config) {
        return config.getDefaultTemperature() == null ? BigDecimal.valueOf(0.7) : config.getDefaultTemperature();
    }
}
