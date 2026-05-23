package com.ls.agent.core.quota.command;

public record ReserveQuotaCommand(
        String traceId,
        Long tenantId,
        Long applicationId,
        Long userId,
        Long estimatedTokens
) {
}
