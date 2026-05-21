package com.ls.agent.core.identity.api;

public interface ApiKeyGenerator {

    String generate();

    String prefixOf(String apiKey);
}
