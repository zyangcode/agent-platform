package com.ls.agent.core.model.api;

import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;

public interface ModelInvokeService {

    ModelInvokeResult invoke(ModelInvokeCommand command);

    default ModelInvokeResult invoke(ModelInvokeCommand command, ModelStreamCallback streamCallback) {
        return invoke(command);
    }
}
