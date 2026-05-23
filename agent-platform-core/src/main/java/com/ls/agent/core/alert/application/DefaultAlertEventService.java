package com.ls.agent.core.alert.application;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.alert.api.AlertEventService;
import com.ls.agent.core.alert.command.RecordAlertEventCommand;
import com.ls.agent.core.alert.entity.AlertEventEntity;
import com.ls.agent.core.alert.mapper.AlertEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultAlertEventService implements AlertEventService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAlertEventService.class);
    private static final String NOTIFY_STATUS_PENDING = "PENDING";

    private final AlertEventMapper eventMapper;

    public DefaultAlertEventService(AlertEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    @Override
    public void record(RecordAlertEventCommand command) {
        AlertEventEntity entity = new AlertEventEntity();
        entity.setTraceId(command.traceId());
        entity.setTenantId(requireNonNull(command.tenantId(), "tenantId"));
        entity.setApplicationId(command.applicationId());
        entity.setAlertType(requireText(command.alertType(), "alertType"));
        entity.setLevel(requireText(command.level(), "level"));
        entity.setTitle(requireText(command.title(), "title"));
        entity.setContent(requireText(command.content(), "content"));
        entity.setSuggestion(command.suggestion());
        entity.setNotifyStatus(NOTIFY_STATUS_PENDING);
        entity.setRetryCount(0);
        try {
            eventMapper.insert(entity);
        } catch (Exception ex) {
            log.warn("Alert event write failed, traceId={}, alertType={}", command.traceId(), command.alertType(), ex);
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, field + " is required");
        }
        return value;
    }

    private <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, field + " is required");
        }
        return value;
    }
}
