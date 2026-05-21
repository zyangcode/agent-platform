package com.ls.agent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateModelProviderRequest(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 32) String providerType,
        @NotBlank @Size(max = 512) String baseUrl,
        @Size(max = 4096) String apiKey
) {
}
