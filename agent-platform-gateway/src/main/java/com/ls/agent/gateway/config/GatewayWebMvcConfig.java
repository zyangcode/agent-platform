package com.ls.agent.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GatewayWebMvcConfig implements WebMvcConfigurer {

    private static final long STREAM_TIMEOUT_MS = 300_000L;

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(STREAM_TIMEOUT_MS);
    }
}
