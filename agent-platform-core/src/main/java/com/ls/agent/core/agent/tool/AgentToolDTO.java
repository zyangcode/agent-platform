package com.ls.agent.core.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;

public record AgentToolDTO(
        String name,
        String displayName,
        String description,
        AgentToolSourceType sourceType,
        JsonNode parameterSchema,
        AgentToolRiskLevel riskLevel
) {
}
