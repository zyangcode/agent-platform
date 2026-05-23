package com.ls.agent.core.security.dto;

public record SensitiveDataFindingDTO(
        String eventType,
        String location,
        String sourceTextHash,
        String maskedSample
) {
}
