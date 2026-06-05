package com.ls.agent.core.model.application;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.api.ModelStreamCallback;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;
import com.ls.agent.core.model.mapper.ModelConfigMapper;
import com.ls.agent.core.model.mapper.ModelProviderMapper;
import com.ls.agent.core.model.provider.ModelProvider;
import com.ls.agent.core.model.provider.ModelProviderRegistry;
import com.ls.agent.core.model.provider.ProviderRequest;
import com.ls.agent.core.model.provider.ProviderResponse;
import org.springframework.stereotype.Service;

@Service
public class DefaultModelInvokeService implements ModelInvokeService {

    private final ModelConfigMapper configMapper;
    private final ModelProviderMapper providerMapper;
    private final ModelProviderRegistry providerRegistry;

    public DefaultModelInvokeService(
            ModelConfigMapper configMapper,
            ModelProviderMapper providerMapper,
            ModelProviderRegistry providerRegistry
    ) {
        this.configMapper = configMapper;
        this.providerMapper = providerMapper;
        this.providerRegistry = providerRegistry;
    }

    @Override
    public ModelInvokeResult invoke(ModelInvokeCommand command) {
        return invoke(command, null);
    }

    @Override
    public ModelInvokeResult invoke(ModelInvokeCommand command, ModelStreamCallback streamCallback) {
        Long modelConfigId = ModelValidation.requireNonNull(command.modelConfigId(), "modelConfigId");
        ModelConfigEntity config = configMapper.selectById(modelConfigId);
        if (config == null || !ModelConstants.STATUS_ACTIVE.equals(config.getStatus())) {
            throw new BizException(ErrorCode.MODEL_INVOKE_FAILED, "Model config is unavailable");
        }
        ModelProviderEntity provider = providerMapper.selectById(config.getProviderId());
        if (provider == null || !ModelConstants.STATUS_ACTIVE.equals(provider.getStatus())) {
            throw new BizException(ErrorCode.MODEL_INVOKE_FAILED, "Model provider is unavailable");
        }
        ModelProvider modelProvider = providerRegistry.resolve(config, provider);
        if (streamCallback == null) {
            ProviderResponse response = modelProvider.invoke(new ProviderRequest(config, provider, command));
            return toResult(response, config, provider);
        }
        boolean[] streamed = {false};
        ModelStreamCallback trackingCallback = token -> {
            streamed[0] = true;
            streamCallback.onToken(token);
        };
        ProviderResponse response = modelProvider.invoke(
                new ProviderRequest(config, provider, command), trackingCallback);
        ModelInvokeResult result = toResult(response, config, provider);
        if (!streamed[0] && result.assistantMessage() != null && !result.assistantMessage().isBlank()) {
            streamCallback.onToken(result.assistantMessage());
        }
        return result;
    }

    private ModelInvokeResult toResult(ProviderResponse response, ModelConfigEntity config, ModelProviderEntity provider) {
        return new ModelInvokeResult(
                config.getId(),
                provider.getId(),
                provider.getProviderType(),
                config.getModelName(),
                response.assistantMessage(),
                response.usage(),
                response.toolCalls()
        );
    }
}
