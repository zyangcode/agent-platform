package com.ls.agent.core.trace.command;

public record QueryTokenUsagePageCommand(
        Long tenantId,
        Long userId,
        Long applicationId,
        Long modelConfigId,
        Long providerId,
        Integer pageNo,
        Integer pageSize
) {
}
