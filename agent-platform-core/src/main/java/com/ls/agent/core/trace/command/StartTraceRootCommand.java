package com.ls.agent.core.trace.command;

import com.fasterxml.jackson.databind.JsonNode;

public record StartTraceRootCommand(
        String traceId,
        Long tenantId,
        Long applicationId,
        Long userId,
        Long profileId,
        Long conversationId,
        String clientRequestId,
        String entrypoint,
        String agentMode,
        JsonNode metadata
) {
}
