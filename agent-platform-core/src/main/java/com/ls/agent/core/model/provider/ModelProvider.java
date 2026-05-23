package com.ls.agent.core.model.provider;

import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;

public interface ModelProvider {

    boolean supports(ModelConfigEntity config, ModelProviderEntity provider);

    ProviderResponse invoke(ProviderRequest request);
}
