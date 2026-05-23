package com.ls.agent.core.quota.command;
import java.time.LocalDateTime;

public record QueryTokenUsageSummaryCommand(
        Long tenantId,
        Long userId,
        Long applicationId,
        LocalDateTime startedFrom,
        LocalDateTime startedTo
) {
}
