package com.ls.agent.core.profile.command;

import com.fasterxml.jackson.databind.JsonNode;

public record CreateProfileCommand(
        Long tenantId,
        Long ownerUserId,
        Long applicationId,
        String name,
        String profileType,
        String description,
        Long modelConfigId,
        String promptExtra,
        JsonNode memoryStrategy,
        Integer maxSteps,
        String visibility
) {
}
