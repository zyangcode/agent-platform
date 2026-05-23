package com.ls.agent.core.model.provider;

import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;

public record ProviderRequest(
        ModelConfigEntity config,
        ModelProviderEntity provider,
        ModelInvokeCommand command
) {
}
