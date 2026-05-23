package com.ls.agent.core.model.provider;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;

import java.util.List;

public class ModelProviderRegistry {

    private final List<ModelProvider> providers;

    public ModelProviderRegistry(List<ModelProvider> providers) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
    }

    public ModelProvider resolve(ModelConfigEntity config, ModelProviderEntity provider) {
        return providers.stream()
                .filter(candidate -> candidate.supports(config, provider))
                .findFirst()
                .orElseThrow(() -> new BizException(
                        ErrorCode.MODEL_INVOKE_FAILED,
                        "Model provider type is unsupported"));
    }
}
