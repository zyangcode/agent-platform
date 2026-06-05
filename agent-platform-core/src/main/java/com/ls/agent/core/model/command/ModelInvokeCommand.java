package com.ls.agent.core.model.command;

import com.ls.agent.core.model.dto.ModelMessage;

import java.math.BigDecimal;
import java.util.List;
import com.ls.agent.core.model.dto.ModelToolSpecDTO;

public record ModelInvokeCommand(
        Long modelConfigId,
        List<ModelMessage> messages,
        BigDecimal temperature,
        boolean stream,
        List<ModelToolSpecDTO> tools
) {

    public ModelInvokeCommand(
            Long modelConfigId,
            List<ModelMessage> messages,
            BigDecimal temperature,
            boolean stream
    ) {
        this(modelConfigId, messages, temperature, stream, List.of());
    }

    public ModelInvokeCommand {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
