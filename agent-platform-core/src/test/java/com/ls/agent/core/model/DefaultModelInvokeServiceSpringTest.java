package com.ls.agent.core.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.model.application.DefaultModelInvokeService;
import com.ls.agent.core.model.application.ModelConstants;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;
import com.ls.agent.core.model.mapper.ModelConfigMapper;
import com.ls.agent.core.model.mapper.ModelProviderMapper;
import com.ls.agent.core.model.provider.MockModelProvider;
import com.ls.agent.core.model.provider.ModelProviderRegistry;
import com.ls.agent.core.model.provider.OpenAiCompatibleProvider;
import com.ls.agent.core.support.security.SecretEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(DefaultModelInvokeServiceSpringTest.Config.class)
class DefaultModelInvokeServiceSpringTest {

    @jakarta.annotation.Resource
    private DefaultModelInvokeService service;

    @jakarta.annotation.Resource
    private ModelConfigMapper modelConfigMapper;

    @jakarta.annotation.Resource
    private ModelProviderMapper modelProviderMapper;

    @jakarta.annotation.Resource
    private ModelProviderRegistry providerRegistry;

    @Test
    void createsServiceWithSpringConstructorInjection() {
        assertThat(service).isNotNull();
        assertThat(providerRegistry).isNotNull();
    }

    @Test
    void invokesMockProviderThroughSpringWiredRegistry() {
        when(modelConfigMapper.selectById(1L)).thenReturn(mockConfig());
        when(modelProviderMapper.selectById(1L)).thenReturn(mockProvider());

        ModelInvokeResult result = service.invoke(new ModelInvokeCommand(
                1L,
                List.of(new ModelMessage("user", "hello spring registry")),
                BigDecimal.valueOf(0.7),
                false
        ));

        assertThat(result.assistantMessage()).isEqualTo("[mock-chat] Echo: hello spring registry");
        assertThat(result.modelConfigId()).isEqualTo(1L);
        assertThat(result.providerId()).isEqualTo(1L);
        assertThat(result.providerType()).isEqualTo(ModelConstants.PROVIDER_OPENAI_COMPATIBLE);
        assertThat(result.modelName()).isEqualTo(ModelConstants.MODEL_MOCK_CHAT);
    }

    @Configuration
    @Import({
            DefaultModelInvokeService.class,
            ModelProviderRegistry.class,
            MockModelProvider.class,
            OpenAiCompatibleProvider.class
    })
    static class Config {

        @Bean
        ModelConfigMapper modelConfigMapper() {
            return mock(ModelConfigMapper.class);
        }

        @Bean
        ModelProviderMapper modelProviderMapper() {
            return mock(ModelProviderMapper.class);
        }

        @Bean
        SecretEncryptor secretEncryptor() {
            return mock(SecretEncryptor.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private ModelConfigEntity mockConfig() {
        ModelConfigEntity config = new ModelConfigEntity();
        config.setId(1L);
        config.setProviderId(1L);
        config.setModelName(ModelConstants.MODEL_MOCK_CHAT);
        config.setDisplayName("Mock Chat Model");
        config.setDefaultTemperature(BigDecimal.valueOf(0.7));
        config.setMaxContextTokens(8192);
        config.setStatus(ModelConstants.STATUS_ACTIVE);
        return config;
    }

    private static ModelProviderEntity mockProvider() {
        ModelProviderEntity provider = new ModelProviderEntity();
        provider.setId(1L);
        provider.setProviderType(ModelConstants.PROVIDER_OPENAI_COMPATIBLE);
        provider.setName("Local Mock Provider");
        provider.setBaseUrl("http://localhost:11434/v1");
        provider.setStatus(ModelConstants.STATUS_ACTIVE);
        return provider;
    }
}
