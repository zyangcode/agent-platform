package com.ls.agent.core.model.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;
import com.ls.agent.core.model.mapper.ModelConfigMapper;
import com.ls.agent.core.model.mapper.ModelProviderMapper;
import com.ls.agent.core.model.provider.MockModelProvider;
import com.ls.agent.core.model.provider.ModelProvider;
import com.ls.agent.core.model.provider.ModelProviderRegistry;
import com.ls.agent.core.model.provider.OpenAiCompatibleProvider;
import com.ls.agent.core.model.provider.ProviderRequest;
import com.ls.agent.core.model.provider.ProviderResponse;
import com.ls.agent.core.support.security.SecretEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultModelInvokeService implements ModelInvokeService {

    private final ModelConfigMapper configMapper;
    private final ModelProviderMapper providerMapper;
    private final ModelProviderRegistry providerRegistry;

    @Autowired
    public DefaultModelInvokeService(
            ModelConfigMapper configMapper,
            ModelProviderMapper providerMapper,
            SecretEncryptor secretEncryptor,
            ObjectMapper objectMapper
    ) {
        this(
                configMapper,
                providerMapper,
                new ModelProviderRegistry(List.of(
                        new MockModelProvider(),
                        new OpenAiCompatibleProvider(secretEncryptor, objectMapper)
                ))
        );
    }

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
        ProviderResponse response = modelProvider.invoke(new ProviderRequest(config, provider, command));
        return new ModelInvokeResult(
                config.getId(),
                provider.getId(),
                provider.getProviderType(),
                config.getModelName(),
                response.assistantMessage(),
                response.usage()
        );
    }
}
