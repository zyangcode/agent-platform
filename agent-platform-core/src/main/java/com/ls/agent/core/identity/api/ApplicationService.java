package com.ls.agent.core.identity.api;

import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.identity.command.CreateApplicationCommand;
import com.ls.agent.core.identity.command.UpdateApplicationCommand;
import com.ls.agent.core.identity.dto.ApiKeyDTO;
import com.ls.agent.core.identity.dto.ApplicationDTO;
import com.ls.agent.core.identity.dto.CreateApplicationResult;
import com.ls.agent.core.identity.dto.RevokeApiKeyResult;

import java.util.List;

public interface ApplicationService {

    CreateApplicationResult createApplication(CreateApplicationCommand command);

    PageResult<ApplicationDTO> pageApplications(Long tenantId, Long ownerUserId, int pageNo, int pageSize);

    ApplicationDTO updateApplication(UpdateApplicationCommand command);

    ApplicationDTO disableApplication(Long tenantId, Long ownerUserId, Long applicationId);

    void ensureApplicationOwned(Long tenantId, Long ownerUserId, Long applicationId);

    List<ApiKeyDTO> listApiKeys(Long tenantId, Long ownerUserId, Long applicationId);

    RevokeApiKeyResult revokeApiKey(Long tenantId, Long ownerUserId, Long applicationId, Long apiKeyId);
}
