package com.ls.agent.core.experience.api;

import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.experience.command.CreateExperienceSkillCommand;
import com.ls.agent.core.experience.command.UpdateExperienceSkillCommand;
import com.ls.agent.core.experience.dto.ExperienceSkillDTO;

public interface ExperienceSkillService {

    ExperienceSkillDTO create(CreateExperienceSkillCommand command);

    PageResult<ExperienceSkillDTO> page(Long tenantId, Long ownerUserId, Long applicationId, int pageNo, int pageSize);

    ExperienceSkillDTO update(UpdateExperienceSkillCommand command);

    ExperienceSkillDTO disable(Long tenantId, Long ownerUserId, Long applicationId, Long experienceSkillId);
}
