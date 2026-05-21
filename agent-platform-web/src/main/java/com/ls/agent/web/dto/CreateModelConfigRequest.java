package com.ls.agent.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateModelConfigRequest(
        @NotNull Long providerId,
        @NotBlank @Size(max = 128) String modelName,
        @NotBlank @Size(max = 128) String displayName,
        @Size(max = 4096) String capabilitiesJson,
        BigDecimal defaultTemperature,
        @NotNull @Min(1) @Max(2000000) Integer maxContextTokens
) {
}
