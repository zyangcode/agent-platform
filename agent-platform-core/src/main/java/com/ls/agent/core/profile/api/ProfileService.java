package com.ls.agent.core.profile.api;

import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.profile.command.BindMcpToolsCommand;
import com.ls.agent.core.profile.command.BindSkillsCommand;
import com.ls.agent.core.profile.command.CreateProfileCommand;
import com.ls.agent.core.profile.dto.ProfileDTO;

public interface ProfileService {

    ProfileDTO createProfile(CreateProfileCommand command);

    PageResult<ProfileDTO> pageProfiles(Long tenantId, Long ownerUserId, Long applicationId, int pageNo, int pageSize);

    ProfileDTO getProfile(Long tenantId, Long ownerUserId, Long profileId);

    void bindSkills(BindSkillsCommand command);

    void bindMcpTools(BindMcpToolsCommand command);
}
