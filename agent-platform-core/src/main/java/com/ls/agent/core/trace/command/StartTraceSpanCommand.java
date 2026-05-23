package com.ls.agent.core.trace.command;

import com.fasterxml.jackson.databind.JsonNode;

public record StartTraceSpanCommand(
        String traceId,
        Long parentSpanId,
        String spanName,
        String spanType,
        String component,
        JsonNode attributes
) {
}
