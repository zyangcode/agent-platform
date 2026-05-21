package com.ls.agent.core.identity.api;

public interface PasswordHasher {

    String hash(String rawValue);

    boolean matches(String rawValue, String hashedValue);
}
