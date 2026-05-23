package com.ls.agent.core.security.application;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.security.api.SecurityEventService;
import com.ls.agent.core.security.command.RecordSecurityEventCommand;
import com.ls.agent.core.security.entity.SecurityEventEntity;
import com.ls.agent.core.security.mapper.SecurityEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultSecurityEventService implements SecurityEventService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSecurityEventService.class);

    private final SecurityEventMapper eventMapper;

    public DefaultSecurityEventService(SecurityEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    @Override
    public void record(RecordSecurityEventCommand command) {
        SecurityEventEntity entity = new SecurityEventEntity();
        entity.setTraceId(command.traceId());
        entity.setTenantId(requireNonNull(command.tenantId(), "tenantId"));
        entity.setApplicationId(command.applicationId());
        entity.setUserId(command.userId());
        entity.setEventType(requireText(command.eventType(), "eventType"));
        entity.setLocation(requireText(command.location(), "location"));
        entity.setSourceTextHash(requireText(command.sourceTextHash(), "sourceTextHash"));
        entity.setMaskedSample(requireText(command.maskedSample(), "maskedSample"));
        entity.setAction(requireText(command.action(), "action"));
        try {
            eventMapper.insert(entity);
        } catch (Exception ex) {
            log.warn("Security event write failed, traceId={}, eventType={}", command.traceId(), command.eventType(), ex);
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
