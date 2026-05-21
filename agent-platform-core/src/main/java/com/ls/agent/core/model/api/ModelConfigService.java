package com.ls.agent.core.model.api;

import com.ls.agent.core.model.command.CreateModelConfigCommand;
import com.ls.agent.core.model.command.CreateModelProviderCommand;
import com.ls.agent.core.model.dto.ModelConfigDTO;
import com.ls.agent.core.model.dto.ModelProviderDTO;

import java.util.List;

public interface ModelConfigService {

    ModelProviderDTO createProvider(CreateModelProviderCommand command);

    ModelConfigDTO createModelConfig(CreateModelConfigCommand command);

    ModelConfigDTO getActiveModelConfig(Long modelConfigId);

    List<ModelConfigDTO> listActiveModelConfigs();
}
