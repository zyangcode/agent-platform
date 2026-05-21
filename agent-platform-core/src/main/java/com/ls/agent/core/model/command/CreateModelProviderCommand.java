package com.ls.agent.core.model.command;

public record CreateModelProviderCommand(
        String name,
        String providerType,
        String baseUrl,
        String apiKey
) {
}
