package com.ls.agent.core.model.provider;

import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;
import com.ls.agent.core.model.api.ModelStreamCallback;

public interface ModelProvider {

    boolean supports(ModelConfigEntity config, ModelProviderEntity provider);

    ProviderResponse invoke(ProviderRequest request);

    default ProviderResponse invoke(ProviderRequest request, ModelStreamCallback streamCallback) {
        ProviderResponse response = invoke(request);
        if (streamCallback != null && response.assistantMessage() != null && !response.assistantMessage().isEmpty()) {
            streamCallback.onToken(response.assistantMessage());
        }
        return response;
    }
}
