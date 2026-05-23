package com.ls.agent.core.trace.command;

public record FinishTraceSpanCommand(
        Long spanId,
        String status,
        String errorCode,
        String errorMessage
) {
}
