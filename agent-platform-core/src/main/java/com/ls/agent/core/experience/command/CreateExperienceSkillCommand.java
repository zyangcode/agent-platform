package com.ls.agent.core.experience.command;

import java.util.List;

public record CreateExperienceSkillCommand(
        Long tenantId,
        Long ownerUserId,
        Long applicationId,
        Long profileId,
        String code,
        String name,
        String domain,
        List<String> triggerKeywords,
        String content
) {
    public CreateExperienceSkillCommand {
        triggerKeywords = triggerKeywords == null ? List.of() : List.copyOf(triggerKeywords);
    }
}
