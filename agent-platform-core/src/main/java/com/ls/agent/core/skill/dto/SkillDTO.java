package com.ls.agent.core.skill.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record SkillDTO(
        Long skillId,
        String code,
        String name,
        String description,
        String skillType,
        String scope,
        String status,
        JsonNode parameterSchema
) {
}
