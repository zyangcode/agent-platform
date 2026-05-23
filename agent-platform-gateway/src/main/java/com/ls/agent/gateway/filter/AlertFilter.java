package com.ls.agent.gateway.filter;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.alert.api.AlertEventService;
import com.ls.agent.core.alert.command.RecordAlertEventCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AlertFilter {

    private static final Logger log = LoggerFactory.getLogger(AlertFilter.class);

    private static final String LEVEL_WARN = "WARN";
    private static final String LEVEL_ERROR = "ERROR";
    private static final String ALERT_TYPE_SECURITY_BLOCKED = "SECURITY_BLOCKED";
    private static final String ALERT_TYPE_TOKEN_EXCEEDED = "TOKEN_EXCEEDED";
    private static final String ALERT_TYPE_MODEL_ERROR = "MODEL_ERROR";

    private final AlertEventService alertEventService;

    public AlertFilter(AlertEventService alertEventService) {
        this.alertEventService = alertEventService;
    }

    public void recordFailure(String traceId, Long tenantId, Long applicationId, Exception exception) {
        if (exception instanceof BizException bizException) {
            if (ErrorCode.SECURITY_BLOCKED.getCode().equals(bizException.getCode())) {
                record(traceId, tenantId, applicationId, ALERT_TYPE_SECURITY_BLOCKED, LEVEL_WARN,
                        "Sensitive request blocked", bizException.getMessage(), "Review security events by traceId");
                return;
            }
            if (ErrorCode.QUOTA_EXCEEDED.getCode().equals(bizException.getCode())) {
                record(traceId, tenantId, applicationId, ALERT_TYPE_TOKEN_EXCEEDED, LEVEL_WARN,
                        "Token quota exceeded", bizException.getMessage(), "Review quota config and token usage");
                return;
            }
        }
        record(traceId, tenantId, applicationId, ALERT_TYPE_MODEL_ERROR, LEVEL_ERROR,
                "Agent runtime failed", errorMessage(exception), "Check trace detail and model provider status");
    }

    private void record(
            String traceId,
            Long tenantId,
            Long applicationId,
            String alertType,
            String level,
            String title,
            String content,
            String suggestion
    ) {
        try {
            alertEventService.record(new RecordAlertEventCommand(
                    traceId,
                    tenantId,
                    applicationId,
                    alertType,
                    level,
                    title,
                    content,
                    suggestion
            ));
        } catch (Exception ex) {
            log.warn("Alert event record failed, traceId={}, alertType={}", traceId, alertType, ex);
        }
    }

    private String errorMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "Runtime failed";
        }
        return exception.getMessage();
    }
}
