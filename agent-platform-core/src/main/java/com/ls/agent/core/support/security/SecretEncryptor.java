package com.ls.agent.core.support.security;

public interface SecretEncryptor {

    String encrypt(String plainText);

    String decrypt(String encryptedText);
}
