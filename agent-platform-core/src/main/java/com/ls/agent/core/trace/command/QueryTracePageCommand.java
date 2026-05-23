package com.ls.agent.core.trace.command;

public record QueryTracePageCommand(
        Long tenantId,
        Long userId,
        Long applicationId,
        Long profileId,
        Long modelConfigId,
        String status,
        String entrypoint,
        Integer pageNo,
        Integer pageSize
) {
}
