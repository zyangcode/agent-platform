package com.ls.agent.core.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.model.application.DefaultModelConfigService;
import com.ls.agent.core.model.command.CreateModelConfigCommand;
import com.ls.agent.core.model.command.CreateModelProviderCommand;
import com.ls.agent.core.model.dto.ModelConfigDTO;
import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;
import com.ls.agent.core.model.mapper.ModelConfigMapper;
import com.ls.agent.core.model.mapper.ModelProviderMapper;
import com.ls.agent.core.support.security.SecretEncryptor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultModelConfigServiceTest {

    private final ModelProviderMapper providerMapper = mock(ModelProviderMapper.class);
    private final ModelConfigMapper configMapper = mock(ModelConfigMapper.class);
    private final SecretEncryptor secretEncryptor = mock(SecretEncryptor.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultModelConfigService service = new DefaultModelConfigService(
            providerMapper,
            configMapper,
            secretEncryptor,
            objectMapper
    );

    @Test
    void createProviderEncryptsApiKeyBeforePersisting() {
        when(secretEncryptor.encrypt("sk-live")).thenReturn("encrypted-sk-live");

        service.createProvider(new CreateModelProviderCommand(
                "OpenAI",
                "OPENAI_COMPATIBLE",
                "https://api.openai.com/v1",
                "sk-live"
        ));

        org.mockito.ArgumentCaptor<ModelProviderEntity> captor =
                org.mockito.ArgumentCaptor.forClass(ModelProviderEntity.class);
        verify(providerMapper).insert(captor.capture());
        assertThat(captor.getValue().getApiKeyEncrypted()).isEqualTo("encrypted-sk-live");
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void createModelConfigPersistsCapabilitiesAsJsonNode() {
        when(providerMapper.selectById(1L)).thenReturn(activeProvider());

        service.createModelConfig(new CreateModelConfigCommand(
                1L,
                "gpt-4o-mini",
                "GPT 4o Mini",
                "{\"text\":true,\"stream\":true}",
                new BigDecimal("0.70"),
                128000
        ));

        org.mockito.ArgumentCaptor<ModelConfigEntity> captor =
                org.mockito.ArgumentCaptor.forClass(ModelConfigEntity.class);
        verify(configMapper).insert(captor.capture());
        assertThat(captor.getValue().getCapabilities().get("text").asBoolean()).isTrue();
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void listActiveModelConfigsReturnsOnlyActiveConfigs() {
        ModelConfigEntity config = new ModelConfigEntity();
        config.setId(10L);
        config.setProviderId(1L);
        config.setModelName("mock-chat");
        config.setDisplayName("Mock Chat Model");
        config.setCapabilities(objectMapper.createObjectNode().put("text", true));
        config.setDefaultTemperature(new BigDecimal("0.70"));
        config.setMaxContextTokens(8192);
        config.setStatus("ACTIVE");
        when(configMapper.selectList(any())).thenReturn(List.of(config));

        List<ModelConfigDTO> results = service.listActiveModelConfigs();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).modelConfigId()).isEqualTo(10L);
        assertThat(results.get(0).modelName()).isEqualTo("mock-chat");
    }

    private static ModelProviderEntity activeProvider() {
        ModelProviderEntity provider = new ModelProviderEntity();
        provider.setId(1L);
        provider.setName("OpenAI");
        provider.setProviderType("OPENAI_COMPATIBLE");
        provider.setBaseUrl("https://api.openai.com/v1");
        provider.setStatus("ACTIVE");
        return provider;
    }
}
