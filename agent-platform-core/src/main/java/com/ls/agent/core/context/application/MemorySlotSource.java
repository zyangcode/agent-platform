package com.ls.agent.core.context.application;

import com.ls.agent.core.context.api.ContextSlotSource;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.ContextSlot;
import com.ls.agent.core.context.dto.ContextSlotContent;
import com.ls.agent.core.context.dto.ContextSlotKind;
import com.ls.agent.core.memory.dto.MemoryDTO;

import java.util.List;

public class MemorySlotSource implements ContextSlotSource {

    private final List<MemoryDTO> memories;

    public MemorySlotSource(List<MemoryDTO> memories) {
        this.memories = memories == null ? List.of() : List.copyOf(memories);
    }

    @Override
    public boolean supports(ContextSlotKind kind) {
        return ContextSlotKind.TASK_MEMORY.equals(kind);
    }

    @Override
    public ContextSlotContent fetch(ContextSlot slot, BuildAgentContextCommand command) {
        if (!supports(slot.kind()) || memories.isEmpty()) {
            return ContextSlotContent.empty(slot.kind());
        }
        StringBuilder builder = new StringBuilder("Long-term memories:\n");
        int used = 0;
        boolean truncated = false;
        for (MemoryDTO memory : memories) {
            int tokens = estimateTokens(memory.content());
            if (used + tokens > slot.tokenBudget()) {
                truncated = true;
                break;
            }
            builder.append("- ").append(memory.content()).append('\n');
            used += tokens;
        }
        if (used == 0) {
            return new ContextSlotContent(ContextSlotKind.TASK_MEMORY, "", 0, truncated);
        }
        return new ContextSlotContent(ContextSlotKind.TASK_MEMORY, builder.toString(), used, truncated);
    }

    static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }
}
