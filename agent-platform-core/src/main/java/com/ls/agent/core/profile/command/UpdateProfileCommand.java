package com.ls.agent.core.profile.command;

import com.fasterxml.jackson.databind.JsonNode;

public record UpdateProfileCommand(
        Long tenantId,
        Long ownerUserId,
        Long profileId,
        String name,
        String description,
        Long modelConfigId,
        String promptExtra,
        JsonNode memoryStrategy,
        Integer maxSteps,
        String visibility
) {
}
