package com.ls.agent.core.context.dto;

public record MicroCompactResult(
        String content,
        boolean compacted,
        int originalChars,
        int compactedChars
) {
}
