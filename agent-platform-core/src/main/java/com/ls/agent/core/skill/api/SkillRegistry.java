package com.ls.agent.core.skill.api;

import com.ls.agent.core.skill.dto.SkillDTO;

import java.util.List;

public interface SkillRegistry {

    List<SkillDTO> listAvailableSkills(Long tenantId, List<Long> skillIds);
}
