package com.ls.agent.core.experience.application;

import com.ls.agent.core.experience.api.ExperienceSkillResolver;
import com.ls.agent.core.experience.dto.ExperienceSkillDTO;

import java.util.List;

public class NoopExperienceSkillResolver implements ExperienceSkillResolver {

    @Override
    public List<ExperienceSkillDTO> resolve(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String profileType,
            String userInput,
            int limit
    ) {
        return List.of();
    }
}
