package com.ls.agent.core.agent.hook;

public record ModelHookContext(
        String traceId,
        Long tenantId,
        Long applicationId,
        Long userId,
        Long profileId,
        Long modelConfigId,
        int step,
        int messageCount,
        int toolSpecCount,
        boolean stream
) {
}
