package com.ls.agent.core.identity.api;

import com.ls.agent.core.identity.dto.ApiKeyAuthResult;

public interface ApiKeyService {

    ApiKeyAuthResult authenticate(String apiKey);
}
