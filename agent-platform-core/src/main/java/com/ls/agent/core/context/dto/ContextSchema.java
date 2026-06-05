package com.ls.agent.core.context.dto;

import java.util.List;
import java.util.Optional;

public record ContextSchema(
        String name,
        List<ContextSlot> slots
) {

    public ContextSchema {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    public Optional<ContextSlot> slot(ContextSlotKind kind) {
        return slots.stream()
                .filter(slot -> slot.kind() == kind)
                .findFirst();
    }

    public static ContextSchema chatSchema() {
        return new ContextSchema("chat", List.of(
                ContextSlot.required(ContextSlotKind.PROFILE, 600),
                ContextSlot.of(ContextSlotKind.HISTORY, 1_600),
                ContextSlot.of(ContextSlotKind.PREFERENCE, 300),
                ContextSlot.of(ContextSlotKind.TASK_MEMORY, 600)
        ));
    }

    public static ContextSchema toolSchema() {
        return new ContextSchema("tool", List.of(
                ContextSlot.required(ContextSlotKind.PROFILE, 600),
                ContextSlot.of(ContextSlotKind.TOOLS, 800),
                ContextSlot.of(ContextSlotKind.TOOL_STATE, 400),
                ContextSlot.of(ContextSlotKind.TASK_MEMORY, 600)
        ));
    }

    public static ContextSchema reactSchema() {
        return new ContextSchema("react", List.of(
                ContextSlot.required(ContextSlotKind.PROFILE, 600),
                ContextSlot.of(ContextSlotKind.HISTORY, 1_600),
                ContextSlot.of(ContextSlotKind.PREFERENCE, 300),
                ContextSlot.of(ContextSlotKind.TASK_MEMORY, 600),
                ContextSlot.of(ContextSlotKind.TOOLS, 800),
                ContextSlot.of(ContextSlotKind.EXPERIENCE, 500)
        ));
    }

    public static ContextSchema ragSchema() {
        return new ContextSchema("rag", List.of(
                ContextSlot.required(ContextSlotKind.PROFILE, 600),
                ContextSlot.of(ContextSlotKind.HISTORY, 1_200),
                ContextSlot.of(ContextSlotKind.RAG_RECALL, 900),
                ContextSlot.of(ContextSlotKind.CONSTRAINTS, 300)
        ));
    }
}
