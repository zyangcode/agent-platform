package com.ls.agent.core.model.dto;

public record ModelProviderDTO(
        Long providerId,
        String name,
        String providerType,
        String baseUrl,
        String status
) {
}
