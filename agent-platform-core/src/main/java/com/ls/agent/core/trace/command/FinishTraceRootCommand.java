package com.ls.agent.core.trace.command;

public record FinishTraceRootCommand(
        String traceId,
        Long conversationId,
        String status,
        String errorCode,
        String errorMessage
) {
}
