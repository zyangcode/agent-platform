package com.ls.agent.core.model.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record ModelToolCallDTO(
        String sourceType,
        String name,
        JsonNode arguments
) {
}
