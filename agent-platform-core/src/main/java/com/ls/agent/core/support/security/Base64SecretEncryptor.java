package com.ls.agent.core.support.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class Base64SecretEncryptor implements SecretEncryptor {

    private static final String PREFIX = "base64:";

    @Override
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        return PREFIX + Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return null;
        }
        if (!encryptedText.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unsupported encrypted secret format");
        }
        byte[] decoded = Base64.getDecoder().decode(encryptedText.substring(PREFIX.length()));
        return new String(decoded, StandardCharsets.UTF_8);
    }
}
