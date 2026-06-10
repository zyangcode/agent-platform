package com.ls.agent.core.model;

import com.ls.agent.core.model.application.ModelConstants;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.model.dto.ModelToolSpecDTO;
import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;
import com.ls.agent.core.model.provider.ProviderRequest;
import com.ls.agent.core.model.provider.ProviderResponse;
import com.ls.agent.core.model.provider.SpringAiChatModelFactory;
import com.ls.agent.core.model.provider.SpringAiModelProvider;
import com.ls.agent.core.support.security.SecretEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiModelProviderTest {

    @Test
    void supportsOnlySpringAiProviderType() {
        SpringAiModelProvider provider = new SpringAiModelProvider(passThroughEncryptor(), fixedFactory(new FakeChatModel()));
        ModelProviderEntity springAi = providerEntity(ModelConstants.PROVIDER_SPRING_AI);
        ModelProviderEntity openAiCompatible = providerEntity(ModelConstants.PROVIDER_OPENAI_COMPATIBLE);

        assertThat(provider.supports(config(), springAi)).isTrue();
        assertThat(provider.supports(config(), openAiCompatible)).isFalse();
    }

    @Test
    void invokesChatModelWithMappedPromptAndUsageWithoutToolCallbacks() {
        FakeChatModel chatModel = new FakeChatModel();
        chatModel.callResponses.add(response("hello from spring ai", usage(12, 5)));
        SpringAiModelProvider provider = new SpringAiModelProvider(passThroughEncryptor(), fixedFactory(chatModel));

        ProviderResponse response = provider.invoke(new ProviderRequest(
                config(),
                providerEntity(ModelConstants.PROVIDER_SPRING_AI),
                new ModelInvokeCommand(
                        10L,
                        List.of(
                                new ModelMessage("system", "You are concise."),
                                new ModelMessage("user", "Read the file."),
                                new ModelMessage("tool", "file content")
                        ),
                        BigDecimal.valueOf(0.2),
                        false,
                        List.of(new ModelToolSpecDTO("MCP", "read_file", "Read file", null))
                )
        ));

        assertThat(response.assistantMessage()).isEqualTo("hello from spring ai");
        assertThat(response.usage().promptTokens()).isEqualTo(12);
        assertThat(response.usage().completionTokens()).isEqualTo(5);
        assertThat(response.usage().totalTokens()).isEqualTo(17);
        assertThat(response.usage().estimated()).isFalse();
        assertThat(response.toolCalls()).isEmpty();
        Prompt prompt = chatModel.lastPrompt.get();
        assertThat(prompt.getInstructions()).hasSize(3);
        assertThat(prompt.getInstructions().get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(prompt.getInstructions().get(1).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(prompt.getInstructions().get(2).getMessageType()).isEqualTo(MessageType.ASSISTANT);
        assertThat(prompt.getInstructions().get(2).getText()).isEqualTo("Tool observation:\nfile content");
        assertThat(prompt.getOptions().getTemperature()).isEqualTo(0.2d);
    }

    @Test
    void streamForwardsOnlyNewTokensAndReturnsCombinedResponse() {
        FakeChatModel chatModel = new FakeChatModel();
        chatModel.streamResponses.add(response("Hel", usage(0, 0)));
        chatModel.streamResponses.add(response("lo", usage(8, 2)));
        SpringAiModelProvider provider = new SpringAiModelProvider(passThroughEncryptor(), fixedFactory(chatModel));
        List<String> tokens = new ArrayList<>();

        ProviderResponse response = provider.invoke(new ProviderRequest(
                config(),
                providerEntity(ModelConstants.PROVIDER_SPRING_AI),
                new ModelInvokeCommand(10L, List.of(new ModelMessage("user", "Hi")), null, true)
        ), tokens::add);

        assertThat(tokens).containsExactly("Hel", "lo");
        assertThat(response.assistantMessage()).isEqualTo("Hello");
        assertThat(response.usage().promptTokens()).isEqualTo(8);
        assertThat(response.usage().completionTokens()).isEqualTo(2);
        assertThat(response.usage().estimated()).isFalse();
    }

    private SpringAiChatModelFactory fixedFactory(ChatModel chatModel) {
        return (config, provider, apiKey) -> {
            assertThat(apiKey).isEqualTo("plain-key");
            return chatModel;
        };
    }

    private SecretEncryptor passThroughEncryptor() {
        return new SecretEncryptor() {
            @Override
            public String encrypt(String plainText) {
                return plainText;
            }

            @Override
            public String decrypt(String encryptedText) {
                return encryptedText;
            }
        };
    }

    private ModelConfigEntity config() {
        ModelConfigEntity config = new ModelConfigEntity();
        config.setId(10L);
        config.setModelName("gpt-test");
        config.setDefaultTemperature(BigDecimal.valueOf(0.7));
        return config;
    }

    private ModelProviderEntity providerEntity(String providerType) {
        ModelProviderEntity provider = new ModelProviderEntity();
        provider.setId(20L);
        provider.setProviderType(providerType);
        provider.setBaseUrl("https://example.test/v1");
        provider.setApiKeyEncrypted("plain-key");
        return provider;
    }

    private ChatResponse response(String text, Usage usage) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().usage(usage).build()
        );
    }

    private Usage usage(int promptTokens, int completionTokens) {
        return new Usage() {
            @Override
            public Integer getPromptTokens() {
                return promptTokens;
            }

            @Override
            public Integer getCompletionTokens() {
                return completionTokens;
            }

            @Override
            public Object getNativeUsage() {
                return null;
            }
        };
    }

    private static class FakeChatModel implements ChatModel {
        private final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();
        private final List<ChatResponse> callResponses = new ArrayList<>();
        private final List<ChatResponse> streamResponses = new ArrayList<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            lastPrompt.set(prompt);
            return callResponses.remove(0);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            lastPrompt.set(prompt);
            return Flux.fromIterable(streamResponses);
        }
    }
}
