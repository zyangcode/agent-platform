package com.ls.agent.core.context.dto;

public record ContextSlotContent(
        ContextSlotKind kind,
        String content,
        int usedTokens,
        boolean truncated
) {

    public ContextSlotContent {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        content = content == null ? "" : content;
        usedTokens = Math.max(0, usedTokens);
    }

    public static ContextSlotContent empty(ContextSlotKind kind) {
        return new ContextSlotContent(kind, "", 0, false);
    }
}
