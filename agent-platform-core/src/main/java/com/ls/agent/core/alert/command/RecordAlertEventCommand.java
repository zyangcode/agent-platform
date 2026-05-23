package com.ls.agent.core.alert.command;

public record RecordAlertEventCommand(
        String traceId,
        Long tenantId,
        Long applicationId,
        String alertType,
        String level,
        String title,
        String content,
        String suggestion
) {
}
