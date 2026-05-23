package com.ls.agent.core.security.command;

public record RecordSecurityEventCommand(
        String traceId,
        Long tenantId,
        Long applicationId,
        Long userId,
        String eventType,
        String location,
        String sourceTextHash,
        String maskedSample,
        String action
) {
}
