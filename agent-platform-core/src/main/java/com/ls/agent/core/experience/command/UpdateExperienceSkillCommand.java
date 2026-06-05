package com.ls.agent.core.experience.command;

import java.util.List;

public record UpdateExperienceSkillCommand(
        Long tenantId,
        Long ownerUserId,
        Long applicationId,
        Long experienceSkillId,
        String code,
        String name,
        String domain,
        List<String> triggerKeywords,
        String content
) {
    public UpdateExperienceSkillCommand {
        triggerKeywords = triggerKeywords == null ? List.of() : List.copyOf(triggerKeywords);
    }
}
