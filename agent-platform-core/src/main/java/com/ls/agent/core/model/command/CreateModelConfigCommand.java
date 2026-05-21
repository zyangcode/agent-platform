package com.ls.agent.core.model.command;

import java.math.BigDecimal;

public record CreateModelConfigCommand(
        Long providerId,
        String modelName,
        String displayName,
        String capabilitiesJson,
        BigDecimal defaultTemperature,
        Integer maxContextTokens
) {
}
