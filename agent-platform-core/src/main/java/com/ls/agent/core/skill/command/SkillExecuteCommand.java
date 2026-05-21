package com.ls.agent.core.skill.command;

import com.fasterxml.jackson.databind.JsonNode;

public record SkillExecuteCommand(
        Long tenantId,
        Long userId,
        String skillCode,
        JsonNode arguments
) {
}
