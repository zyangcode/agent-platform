package com.ls.agent.core.trace.command;

import com.fasterxml.jackson.databind.JsonNode;

public record FinishTraceSpanCommand(
        Long spanId,
        String status,
        String errorCode,
        String errorMessage,
        JsonNode attributes
) {

    public FinishTraceSpanCommand(Long spanId, String status, String errorCode, String errorMessage) {
        this(spanId, status, errorCode, errorMessage, null);
    }
}
