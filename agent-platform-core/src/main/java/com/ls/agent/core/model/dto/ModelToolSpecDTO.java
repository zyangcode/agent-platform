package com.ls.agent.core.model.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record ModelToolSpecDTO(
        String sourceType,
        String name,
        String description,
        JsonNode parameterSchema
) {
}
