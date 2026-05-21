package com.ls.agent.core.identity.command;

public record CreateApplicationCommand(
        Long tenantId,
        Long ownerUserId,
        String name,
        String description
) {
}
