package com.ls.agent.core.context.dto;

public record ContextSlot(
        ContextSlotKind kind,
        boolean required,
        int tokenBudget,
        ContextSlotFilter filter
) {

    public ContextSlot {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (tokenBudget <= 0) {
            throw new IllegalArgumentException("tokenBudget must be positive");
        }
        filter = filter == null ? ContextSlotFilter.empty() : filter;
    }

    public static ContextSlot of(ContextSlotKind kind, int tokenBudget) {
        return new ContextSlot(kind, false, tokenBudget, ContextSlotFilter.empty());
    }

    public static ContextSlot required(ContextSlotKind kind, int tokenBudget) {
        return new ContextSlot(kind, true, tokenBudget, ContextSlotFilter.empty());
    }
}
