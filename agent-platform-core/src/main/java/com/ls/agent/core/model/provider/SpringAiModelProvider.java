package com.ls.agent.core.model.provider;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.model.api.ModelStreamCallback;
import com.ls.agent.core.model.application.ModelConstants;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;
import com.ls.agent.core.support.security.SecretEncryptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(90)
public class SpringAiModelProvider implements ModelProvider {

    private final SecretEncryptor secretEncryptor;
    private final SpringAiChatModelFactory chatModelFactory;

    public SpringAiModelProvider(SecretEncryptor secretEncryptor, SpringAiChatModelFactory chatModelFactory) {
        this.secretEncryptor = secretEncryptor;
        this.chatModelFactory = chatModelFactory;
    }

    @Override
    public boolean supports(ModelConfigEntity config, ModelProviderEntity provider) {
        return provider != null && ModelConstants.PROVIDER_SPRING_AI.equals(provider.getProviderType());
    }

    @Override
    public ProviderResponse invoke(ProviderRequest request) {
        ChatModel chatModel = chatModelFactory.create(request.config(), request.provider(), decryptApiKey(request.provider()));
        ChatResponse response = chatModel.call(prompt(request.config(), request.command()));
        String content = responseText(response);
        return new ProviderResponse(content, usage(response, promptText(request.command()), content));
    }

    @Override
    public ProviderResponse invoke(ProviderRequest request, ModelStreamCallback streamCallback) {
        if (!request.command().stream()) {
            return invoke(request);
        }
        ChatModel chatModel = chatModelFactory.create(request.config(), request.provider(), decryptApiKey(request.provider()));
        StringBuilder assistantMessage = new StringBuilder();
        ModelUsageDTO usage = null;
        for (ChatResponse chunk : chatModel.stream(prompt(request.config(), request.command())).toIterable()) {
            String token = responseText(chunk);
            if (!token.isEmpty()) {
                assistantMessage.append(token);
                if (streamCallback != null) {
                    streamCallback.onToken(token);
                }
            }
            ModelUsageDTO chunkUsage = usage(chunk, promptText(request.command()), assistantMessage.toString());
            if (!chunkUsage.estimated()) {
                usage = chunkUsage;
            }
        }
        String content = assistantMessage.toString();
        if (content.isBlank()) {
            throw new BizException(ErrorCode.MODEL_INVOKE_FAILED, "Model response message is missing");
        }
        if (usage == null) {
            int promptTokens = UsageParser.estimateTokens(promptText(request.command()));
            int completionTokens = UsageParser.estimateTokens(content);
            usage = new ModelUsageDTO(promptTokens, completionTokens, promptTokens + completionTokens, true);
        }
        return new ProviderResponse(content, usage);
    }

    private Prompt prompt(ModelConfigEntity config, ModelInvokeCommand command) {
        return new Prompt(messages(command.messages()), ChatOptions.builder()
                .model(config.getModelName())
                .temperature(effectiveTemperature(config, command).doubleValue())
                .build());
    }

    private List<Message> messages(List<ModelMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of(new UserMessage(""));
        }
        List<Message> result = new ArrayList<>();
        for (ModelMessage message : messages) {
            if (message == null) {
                continue;
            }
            String role = message.role() == null || message.role().isBlank() ? "user" : message.role();
            String content = message.content() == null ? "" : message.content();
            if ("system".equalsIgnoreCase(role)) {
                result.add(new SystemMessage(content));
            } else if ("assistant".equalsIgnoreCase(role)) {
                result.add(new AssistantMessage(content));
            } else if ("tool".equalsIgnoreCase(role)) {
                result.add(new AssistantMessage("Tool observation:\n" + content));
            } else {
                result.add(new UserMessage(content));
            }
        }
        return result.isEmpty() ? List.of(new UserMessage("")) : result;
    }

    private String responseText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private ModelUsageDTO usage(ChatResponse response, String promptText, String content) {
        Usage usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
        int promptTokens = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        int completionTokens = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        int totalTokens = usage == null || usage.getTotalTokens() == null ? promptTokens + completionTokens : usage.getTotalTokens();
        boolean estimated = usage == null || totalTokens == 0;
        if (estimated) {
            promptTokens = UsageParser.estimateTokens(promptText);
            completionTokens = UsageParser.estimateTokens(content);
            totalTokens = promptTokens + completionTokens;
        }
        return new ModelUsageDTO(promptTokens, completionTokens, totalTokens, estimated);
    }

    private BigDecimal effectiveTemperature(ModelConfigEntity config, ModelInvokeCommand command) {
        if (command.temperature() != null) {
            return command.temperature();
        }
        return config.getDefaultTemperature() == null ? BigDecimal.valueOf(0.7) : config.getDefaultTemperature();
    }

    private String promptText(ModelInvokeCommand command) {
        if (command.messages() == null || command.messages().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ModelMessage message : command.messages()) {
            if (message != null && message.content() != null) {
                builder.append(message.content()).append('\n');
            }
        }
        return builder.toString();
    }

    private String decryptApiKey(ModelProviderEntity provider) {
        String apiKey = secretEncryptor.decrypt(provider.getApiKeyEncrypted());
        if (apiKey == null || apiKey.isBlank()) {
            throw new BizException(ErrorCode.MODEL_INVOKE_FAILED, "Model provider API key is missing");
        }
        return apiKey;
    }
}
