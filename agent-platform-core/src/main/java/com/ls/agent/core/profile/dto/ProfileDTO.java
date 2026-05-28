package com.ls.agent.core.profile.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record ProfileDTO(
        Long profileId,
        Long applicationId,
        String name,
        String profileType,
        String description,
        Long modelConfigId,
        String promptExtra,
        JsonNode memoryStrategy,
        Integer maxSteps,
        String executionMode,
        String visibility,
        String status,
        List<ProfileSkillBindingDTO> skillBindings,
        List<ProfileMcpToolBindingDTO> mcpToolBindings
) {
    public ProfileDTO {
        skillBindings = skillBindings == null ? List.of() : List.copyOf(skillBindings);
        mcpToolBindings = mcpToolBindings == null ? List.of() : List.copyOf(mcpToolBindings);
    }
}
