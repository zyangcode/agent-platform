package com.ls.agent.core.experience.api;

import com.ls.agent.core.experience.dto.ExperienceSkillDTO;

import java.util.List;

public interface ExperienceSkillResolver {

    List<ExperienceSkillDTO> resolve(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String profileType,
            String userInput,
            int limit
    );
}
