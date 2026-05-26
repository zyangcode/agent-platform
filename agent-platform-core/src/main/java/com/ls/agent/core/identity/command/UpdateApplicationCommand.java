package com.ls.agent.core.identity.command;

public record UpdateApplicationCommand(
        Long tenantId,
        Long ownerUserId,
        Long applicationId,
        String name,
        String description
) {
}
