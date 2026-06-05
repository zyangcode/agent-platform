package com.ls.agent.core.experience.dto;

public record ExperienceSkillDTO(
        Long experienceSkillId,
        String code,
        String name,
        String domain,
        String content
) {
}
