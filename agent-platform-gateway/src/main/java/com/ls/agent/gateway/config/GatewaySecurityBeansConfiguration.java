package com.ls.agent.gateway.config;

import com.ls.agent.core.identity.api.ApiKeyGenerator;
import com.ls.agent.core.identity.api.PasswordHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.UUID;

@Configuration
public class GatewaySecurityBeansConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    PasswordHasher passwordHasher(PasswordEncoder passwordEncoder) {
        return new PasswordHasher() {
            @Override
            public String hash(String rawValue) {
                return passwordEncoder.encode(rawValue);
            }

            @Override
            public boolean matches(String rawValue, String hashedValue) {
                return passwordEncoder.matches(rawValue, hashedValue);
            }
        };
    }

    @Bean
    ApiKeyGenerator apiKeyGenerator() {
        SecureRandom random = new SecureRandom();
        return new ApiKeyGenerator() {
            @Override
            public String generate() {
                return "sk-" + UUID.randomUUID().toString().replace("-", "") + random.nextInt(10000, 99999);
            }

            @Override
            public String prefixOf(String apiKey) {
                return apiKey.substring(0, Math.min(apiKey.length(), 10));
            }
        };
    }
}
