package com.ls.agent.core.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record AgentToolDTO(
        String name,
        String displayName,
        String description,
        AgentToolSourceType sourceType,
        JsonNode parameterSchema,
        AgentToolRiskLevel riskLevel,
        boolean readOnly,
        List<String> resourceKeys
) {

    public AgentToolDTO(
            String name,
            String displayName,
            String description,
            AgentToolSourceType sourceType,
            JsonNode parameterSchema,
            AgentToolRiskLevel riskLevel
    ) {
        this(name, displayName, description, sourceType, parameterSchema, riskLevel, false, List.of());
    }

    public AgentToolDTO {
        resourceKeys = resourceKeys == null ? List.of() : List.copyOf(resourceKeys);
    }
}
