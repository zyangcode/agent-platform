package com.ls.agent.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProfileRequest(
        @NotNull Long applicationId,
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 32) String profileType,
        @Size(max = 2048) String description,
        @NotNull Long modelConfigId,
        @Size(max = 8000) String promptExtra,
        JsonNode memoryStrategy,
        @Min(1) @Max(50) Integer maxSteps,
        @NotBlank @Size(max = 32) String visibility
) {
}
