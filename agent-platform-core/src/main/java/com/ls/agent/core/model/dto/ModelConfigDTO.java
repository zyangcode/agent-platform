package com.ls.agent.core.model.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

public record ModelConfigDTO(
        Long modelConfigId,
        Long providerId,
        String modelName,
        String displayName,
        JsonNode capabilities,
        BigDecimal defaultTemperature,
        Integer maxContextTokens,
        String status
) {
}
