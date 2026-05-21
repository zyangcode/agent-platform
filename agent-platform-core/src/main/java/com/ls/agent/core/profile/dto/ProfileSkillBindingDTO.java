package com.ls.agent.core.profile.dto;

public record ProfileSkillBindingDTO(
        Long skillId,
        Boolean enabledByDefault,
        Boolean required
) {
}
