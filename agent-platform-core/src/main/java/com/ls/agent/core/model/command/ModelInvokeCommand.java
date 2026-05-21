package com.ls.agent.core.model.command;

import com.ls.agent.core.model.dto.ModelMessage;

import java.math.BigDecimal;
import java.util.List;

public record ModelInvokeCommand(
        Long modelConfigId,
        List<ModelMessage> messages,
        BigDecimal temperature,
        boolean stream
) {
}
