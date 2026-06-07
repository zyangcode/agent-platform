package com.ls.agent.core.experience.dto;

import java.util.List;

public record ExperienceSkillDTO(
        Long experienceSkillId,
        String code,
        String name,
        String domain,
        List<String> triggerKeywords,
        String content
) {
}
