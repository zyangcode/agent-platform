package com.ls.agent.core.skill.command;

import java.nio.file.Path;

public record RegisterJarSkillCommand(
        Long tenantId,
        Long ownerUserId,
        String scope,
        Path jarPath,
        String fileName,
        Long sizeBytes,
        String checksum,
        String manifestJson
) {
}
