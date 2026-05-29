package com.ls.agent.core.model.provider;

import com.ls.agent.core.model.application.ModelConstants;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(0)
public class MockModelProvider implements ModelProvider {

    @Override
    public boolean supports(ModelConfigEntity config, ModelProviderEntity provider) {
        return ModelConstants.MODEL_MOCK_CHAT.equals(config.getModelName());
    }

    @Override
    public ProviderResponse invoke(ProviderRequest request) {
        List<ModelMessage> messages = request.command().messages();
        String lastUserMessage = messages == null ? "" : messages.stream()
                .filter(message -> "user".equals(message.role()))
                .map(ModelMessage::content)
                .reduce((first, second) -> second)
                .orElse("");
        String assistantMessage = "[mock-chat] " + (lastUserMessage.isBlank()
                ? "Hello, this is a mock model response."
                : "Echo: " + lastUserMessage);
        int promptTokens = UsageParser.estimateTokens(lastUserMessage);
        int completionTokens = UsageParser.estimateTokens(assistantMessage);
        return new ProviderResponse(
                assistantMessage,
                new ModelUsageDTO(promptTokens, completionTokens, promptTokens + completionTokens, true)
        );
    }
}
