package com.ls.agent.core.quota.command;

public record CommitQuotaReservationCommand(
        String traceId,
        Long actualTokens
) {
}
