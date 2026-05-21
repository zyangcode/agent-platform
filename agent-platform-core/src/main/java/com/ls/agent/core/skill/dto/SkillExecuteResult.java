package com.ls.agent.core.skill.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record SkillExecuteResult(
        boolean success,
        String skillCode,
        JsonNode output,
        String errorMessage
) {
}
