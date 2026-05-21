package com.ls.agent.web.security;

import com.ls.agent.core.identity.api.ApiKeyGenerator;
import com.ls.agent.core.identity.api.PasswordHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * 安全相关基础 Bean 配置类。
 * 提供了密码编码器、API Key 生成器等基础设施组件。
 */
@Configuration
public class SecurityBeansConfiguration {

    /**
     * 配置密码编码器。
     * 使用 BCrypt 强哈希算法进行密码加密。
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 业务层使用的密码加密/匹配组件。
     * 对 Spring Security 的 PasswordEncoder 进行二次封装，使其更符合项目接口规范。
     */
    @Bean
    PasswordHasher passwordHasher(PasswordEncoder passwordEncoder) {
        return new PasswordHasher() {
            @Override
            public String hash(String rawValue) {
                // 调用 BCrypt 进行加密
                return passwordEncoder.encode(rawValue);
            }

            @Override
            public boolean matches(String rawValue, String hashedValue) {
                // 调用 BCrypt 进行比对
                return passwordEncoder.matches(rawValue, hashedValue);
            }
        };
    }

    /**
     * API Key 生成器组件。
     * 用于生成外部接入所需的 sk- 密钥。
     */
    @Bean
    ApiKeyGenerator apiKeyGenerator() {
        // 使用 SecureRandom 确保密钥的随机安全性
        SecureRandom random = new SecureRandom();
        return new ApiKeyGenerator() {
            @Override
            public String generate() {
                // 格式：sk- + 32位UUID（去横线） + 5位随机数字
                return "sk-" + UUID.randomUUID().toString().replace("-", "") + random.nextInt(10000, 99999);
            }

            @Override
            public String prefixOf(String apiKey) {
                // 提取前 10 位作为前缀，用于界面显示
                return apiKey.substring(0, Math.min(apiKey.length(), 10));
            }
        };
    }
}
