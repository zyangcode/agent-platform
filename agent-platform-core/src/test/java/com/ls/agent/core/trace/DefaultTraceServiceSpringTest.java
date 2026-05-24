package com.ls.agent.core.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.quota.api.TokenUsageService;
import com.ls.agent.core.trace.application.DefaultTraceService;
import com.ls.agent.core.trace.mapper.TraceRootMapper;
import com.ls.agent.core.trace.mapper.TraceSpanMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(DefaultTraceServiceSpringTest.Config.class)
class DefaultTraceServiceSpringTest {

    @jakarta.annotation.Resource
    private DefaultTraceService service;

    @Test
    void createsServiceWithSpringConstructorInjection() {
        assertThat(service).isNotNull();
    }

    @Configuration
    @Import(DefaultTraceService.class)
    static class Config {

        @Bean
        TraceRootMapper traceRootMapper() {
            return mock(TraceRootMapper.class);
        }

        @Bean
        TraceSpanMapper traceSpanMapper() {
            return mock(TraceSpanMapper.class);
        }

        @Bean
        TokenUsageService tokenUsageService() {
            return mock(TokenUsageService.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
