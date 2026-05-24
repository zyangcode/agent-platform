package com.ls.agent.core.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.model.application.DefaultModelInvokeService;
import com.ls.agent.core.model.mapper.ModelConfigMapper;
import com.ls.agent.core.model.mapper.ModelProviderMapper;
import com.ls.agent.core.support.security.SecretEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(DefaultModelInvokeServiceSpringTest.Config.class)
class DefaultModelInvokeServiceSpringTest {

    @jakarta.annotation.Resource
    private DefaultModelInvokeService service;

    @Test
    void createsServiceWithSpringConstructorInjection() {
        assertThat(service).isNotNull();
    }

    @Configuration
    @Import(DefaultModelInvokeService.class)
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
}
