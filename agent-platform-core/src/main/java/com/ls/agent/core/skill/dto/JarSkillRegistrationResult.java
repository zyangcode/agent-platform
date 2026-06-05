package com.ls.agent.core.skill.dto;

public record JarSkillRegistrationResult(
        Long skillId,
        Long skillVersionId,
        String skillCode,
        String status,
        String versionStatus,
        String validationMessage
) {
}
