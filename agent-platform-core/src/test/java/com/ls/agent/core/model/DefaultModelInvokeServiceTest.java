package com.ls.agent.core.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.model.application.DefaultModelInvokeService;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;
import com.ls.agent.core.model.mapper.ModelConfigMapper;
import com.ls.agent.core.model.mapper.ModelProviderMapper;
import com.ls.agent.core.model.provider.ModelProvider;
import com.ls.agent.core.model.provider.ModelProviderRegistry;
import com.ls.agent.core.model.provider.ProviderRequest;
import com.ls.agent.core.model.provider.ProviderResponse;
import com.ls.agent.core.support.security.SecretEncryptor;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultModelInvokeServiceTest {

    private final ModelConfigMapper configMapper = mock(ModelConfigMapper.class);
    private final ModelProviderMapper providerMapper = mock(ModelProviderMapper.class);
    private final SecretEncryptor secretEncryptor = mock(SecretEncryptor.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultModelInvokeService service = new DefaultModelInvokeService(
            configMapper,
            providerMapper,
            secretEncryptor,
            objectMapper
    );

    @Test
    void mockChatReturnsAssistantTextWithoutExternalNetwork() {
        when(configMapper.selectById(1L)).thenReturn(mockConfig());
        when(providerMapper.selectById(1L)).thenReturn(mockProvider());

        ModelInvokeResult result = service.invoke(new ModelInvokeCommand(
                1L,
                List.of(new ModelMessage("user", "hello")),
                BigDecimal.valueOf(0.7),
                false
        ));

        assertThat(result.assistantMessage()).contains("mock-chat");
        assertThat(result.providerId()).isEqualTo(1L);
        assertThat(result.providerType()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(result.modelName()).isEqualTo("mock-chat");
        assertThat(result.usage().estimated()).isTrue();
        assertThat(result.usage().promptTokens()).isGreaterThan(0);
        assertThat(result.usage().completionTokens()).isGreaterThan(0);
        assertThat(result.usage().totalTokens()).isGreaterThan(0);
        assertThat(result.modelConfigId()).isEqualTo(1L);
    }

    @Test
    void openAiCompatibleModelPostsChatCompletionAndParsesResponse() throws Exception {
        List<String> requestBodies = new ArrayList<>();
        List<String> authorizationHeaders = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        server.createContext("/v1/chat/completions", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            writeJson(exchange, 200, """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "real model response"
                          }
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 11,
                        "completion_tokens": 7,
                        "total_tokens": 18
                      }
                    }
                    """);
        });
        server.setExecutor(executor);
        server.start();
        try {
            when(configMapper.selectById(2L)).thenReturn(realConfig());
            when(providerMapper.selectById(2L)).thenReturn(realProvider(server.getAddress().getPort()));
            when(secretEncryptor.decrypt("encrypted-key")).thenReturn("sk-test");

            ModelInvokeResult result = service.invoke(new ModelInvokeCommand(
                    2L,
                    List.of(new ModelMessage("user", "hello real model")),
                    BigDecimal.valueOf(0.2),
                    false
            ));

            assertThat(result.assistantMessage()).isEqualTo("real model response");
            assertThat(result.providerId()).isEqualTo(2L);
            assertThat(result.providerType()).isEqualTo("OPENAI_COMPATIBLE");
            assertThat(result.modelName()).isEqualTo("deepseek-chat");
            assertThat(result.usage().promptTokens()).isEqualTo(11);
            assertThat(result.usage().completionTokens()).isEqualTo(7);
            assertThat(result.usage().totalTokens()).isEqualTo(18);
            assertThat(result.usage().estimated()).isFalse();
            assertThat(authorizationHeaders).containsExactly("Bearer sk-test");
            assertThat(requestBodies).singleElement()
                    .satisfies(body -> {
                        assertThat(body).contains("\"model\":\"deepseek-chat\"");
                        assertThat(body).contains("\"temperature\":0.2");
                        assertThat(body).contains("\"role\":\"user\"");
                        assertThat(body).contains("\"content\":\"hello real model\"");
                    });
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void openAiCompatibleModelConvertsInternalToolMessagesToAssistantContext() throws Exception {
        List<String> requestBodies = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        server.createContext("/v1/chat/completions", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "1+1 equals 2"
                          }
                        }
                      ]
                    }
                    """);
        });
        server.setExecutor(executor);
        server.start();
        try {
            when(configMapper.selectById(2L)).thenReturn(realConfig());
            when(providerMapper.selectById(2L)).thenReturn(realProvider(server.getAddress().getPort()));
            when(secretEncryptor.decrypt("encrypted-key")).thenReturn("sk-test");

            service.invoke(new ModelInvokeCommand(
                    2L,
                    List.of(
                            new ModelMessage("user", "1+1等于几"),
                            new ModelMessage("assistant", "@skill:calculator {\"expression\":\"1+1\"}"),
                            new ModelMessage("tool", "{\"result\":\"2\"}")
                    ),
                    BigDecimal.valueOf(0.2),
                    false
            ));

            assertThat(requestBodies).singleElement()
                    .satisfies(body -> {
                        assertThat(body).doesNotContain("\"role\":\"tool\"");
                        assertThat(body).contains("\"role\":\"assistant\"");
                        assertThat(body).contains("Tool observation:");
                        assertThat(body).contains("{\\\"result\\\":\\\"2\\\"}");
                    });
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void invokeDelegatesToProviderRegistry() {
        ModelProvider customProvider = new ModelProvider() {
            @Override
            public boolean supports(ModelConfigEntity config, ModelProviderEntity provider) {
                return "CUSTOM".equals(provider.getProviderType());
            }

            @Override
            public ProviderResponse invoke(ProviderRequest request) {
                return new ProviderResponse(
                        "custom provider response",
                        new com.ls.agent.core.model.dto.ModelUsageDTO(3, 5, 8, false)
                );
            }
        };
        DefaultModelInvokeService serviceWithRegistry = new DefaultModelInvokeService(
                configMapper,
                providerMapper,
                new ModelProviderRegistry(List.of(customProvider))
        );
        when(configMapper.selectById(3L)).thenReturn(customConfig());
        when(providerMapper.selectById(3L)).thenReturn(customProviderEntity());

        ModelInvokeResult result = serviceWithRegistry.invoke(new ModelInvokeCommand(
                3L,
                List.of(new ModelMessage("user", "hello custom")),
                BigDecimal.valueOf(0.5),
                false
        ));

        assertThat(result.assistantMessage()).isEqualTo("custom provider response");
        assertThat(result.providerId()).isEqualTo(3L);
        assertThat(result.providerType()).isEqualTo("CUSTOM");
        assertThat(result.usage().totalTokens()).isEqualTo(8);
    }

    @Test
    void unknownProviderTypeReturnsBizException() {
        DefaultModelInvokeService serviceWithRegistry = new DefaultModelInvokeService(
                configMapper,
                providerMapper,
                new ModelProviderRegistry(List.of())
        );
        when(configMapper.selectById(3L)).thenReturn(customConfig());
        when(providerMapper.selectById(3L)).thenReturn(customProviderEntity());

        assertThatThrownBy(() -> serviceWithRegistry.invoke(new ModelInvokeCommand(
                3L,
                List.of(new ModelMessage("user", "hello custom")),
                BigDecimal.valueOf(0.5),
                false
        ))).isInstanceOf(com.ls.agent.common.error.BizException.class);
    }

    private ModelConfigEntity mockConfig() {
        ModelConfigEntity config = new ModelConfigEntity();
        config.setId(1L);
        config.setProviderId(1L);
        config.setModelName("mock-chat");
        config.setDisplayName("Mock Chat Model");
        config.setCapabilities(objectMapper.createObjectNode().put("text", true));
        config.setDefaultTemperature(BigDecimal.valueOf(0.7));
        config.setMaxContextTokens(8192);
        config.setStatus("ACTIVE");
        return config;
    }

    private ModelConfigEntity realConfig() {
        ModelConfigEntity config = new ModelConfigEntity();
        config.setId(2L);
        config.setProviderId(2L);
        config.setModelName("deepseek-chat");
        config.setDisplayName("DeepSeek Chat");
        config.setCapabilities(objectMapper.createObjectNode().put("text", true));
        config.setDefaultTemperature(BigDecimal.valueOf(0.7));
        config.setMaxContextTokens(8192);
        config.setStatus("ACTIVE");
        return config;
    }

    private ModelConfigEntity customConfig() {
        ModelConfigEntity config = new ModelConfigEntity();
        config.setId(3L);
        config.setProviderId(3L);
        config.setModelName("custom-chat");
        config.setDisplayName("Custom Chat");
        config.setCapabilities(objectMapper.createObjectNode().put("text", true));
        config.setDefaultTemperature(BigDecimal.valueOf(0.7));
        config.setMaxContextTokens(8192);
        config.setStatus("ACTIVE");
        return config;
    }

    private static ModelProviderEntity mockProvider() {
        ModelProviderEntity provider = new ModelProviderEntity();
        provider.setId(1L);
        provider.setProviderType("OPENAI_COMPATIBLE");
        provider.setName("Local Mock Provider");
        provider.setBaseUrl("http://localhost:11434/v1");
        provider.setStatus("ACTIVE");
        return provider;
    }

    private static ModelProviderEntity realProvider(int port) {
        ModelProviderEntity provider = new ModelProviderEntity();
        provider.setId(2L);
        provider.setProviderType("OPENAI_COMPATIBLE");
        provider.setName("OpenAI Compatible Test Provider");
        provider.setBaseUrl("http://localhost:" + port + "/v1");
        provider.setApiKeyEncrypted("encrypted-key");
        provider.setStatus("ACTIVE");
        return provider;
    }

    private static ModelProviderEntity customProviderEntity() {
        ModelProviderEntity provider = new ModelProviderEntity();
        provider.setId(3L);
        provider.setProviderType("CUSTOM");
        provider.setName("Custom Test Provider");
        provider.setBaseUrl("http://localhost/custom");
        provider.setStatus("ACTIVE");
        return provider;
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
