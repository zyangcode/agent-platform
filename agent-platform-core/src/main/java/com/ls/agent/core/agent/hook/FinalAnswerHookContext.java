package com.ls.agent.core.agent.hook;

public record FinalAnswerHookContext(
        String traceId,
        Long tenantId,
        Long applicationId,
        Long userId,
        Long profileId,
        int rawChars,
        int finalChars,
        boolean changed
) {
}
