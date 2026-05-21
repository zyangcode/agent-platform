package com.ls.agent.core.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.model.application.DefaultModelInvokeService;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;
import com.ls.agent.core.model.mapper.ModelConfigMapper;
import com.ls.agent.core.model.mapper.ModelProviderMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultModelInvokeServiceTest {

    private final ModelConfigMapper configMapper = mock(ModelConfigMapper.class);
    private final ModelProviderMapper providerMapper = mock(ModelProviderMapper.class);
    private final DefaultModelInvokeService service = new DefaultModelInvokeService(configMapper, providerMapper);
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        assertThat(result.usage().estimated()).isTrue();
        assertThat(result.modelConfigId()).isEqualTo(1L);
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

    private static ModelProviderEntity mockProvider() {
        ModelProviderEntity provider = new ModelProviderEntity();
        provider.setId(1L);
        provider.setProviderType("OPENAI_COMPATIBLE");
        provider.setName("Local Mock Provider");
        provider.setBaseUrl("http://localhost:11434/v1");
        provider.setStatus("ACTIVE");
        return provider;
    }
}
