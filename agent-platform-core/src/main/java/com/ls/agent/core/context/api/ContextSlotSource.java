package com.ls.agent.core.context.api;

import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.ContextSlot;
import com.ls.agent.core.context.dto.ContextSlotContent;
import com.ls.agent.core.context.dto.ContextSlotKind;

public interface ContextSlotSource {

    boolean supports(ContextSlotKind kind);

    ContextSlotContent fetch(ContextSlot slot, BuildAgentContextCommand command);
}
