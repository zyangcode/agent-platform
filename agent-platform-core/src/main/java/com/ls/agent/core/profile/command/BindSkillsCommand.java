package com.ls.agent.core.profile.command;

import java.util.List;

public record BindSkillsCommand(
        Long tenantId,
        Long ownerUserId,
        Long profileId,
        List<Long> skillIds
) {
}
