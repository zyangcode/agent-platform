package com.ls.agent.core.skill.api;

import com.ls.agent.core.skill.dto.SkillDTO;

import java.util.List;

public interface SkillQueryService {

    boolean areSkillsBindable(Long tenantId, List<Long> skillIds);

    List<SkillDTO> listSkills(Long tenantId, String scope, String status);
}
