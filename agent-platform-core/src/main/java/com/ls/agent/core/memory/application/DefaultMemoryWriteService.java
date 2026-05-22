package com.ls.agent.core.memory.application;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.memory.api.MemoryWriteService;
import com.ls.agent.core.memory.command.RecordMemoryCommand;
import com.ls.agent.core.memory.entity.MemoryEntity;
import com.ls.agent.core.memory.mapper.MemoryMapper;
import org.springframework.stereotype.Service;

@Service
public class DefaultMemoryWriteService implements MemoryWriteService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final MemoryMapper memoryMapper;

    public DefaultMemoryWriteService(MemoryMapper memoryMapper) {
        this.memoryMapper = memoryMapper;
    }

    @Override
    public void record(RecordMemoryCommand command) {
        validate(command);
        MemoryEntity entity = new MemoryEntity();
        entity.setTenantId(command.tenantId());
        entity.setUserId(command.userId());
        entity.setApplicationId(command.applicationId());
        entity.setProfileId(command.profileId());
        entity.setMemoryType(command.memoryType());
        entity.setContent(command.content());
        entity.setSourceConversationId(command.sourceConversationId());
        entity.setStatus(STATUS_ACTIVE);
        memoryMapper.insert(entity);
    }

    private void validate(RecordMemoryCommand command) {
        requireNonNull(command.tenantId(), "tenantId");
        requireNonNull(command.userId(), "userId");
        if (command.memoryType() == null || command.memoryType().isBlank()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "memoryType is required");
        }
        if (command.content() == null || command.content().isBlank()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "content is required");
        }
    }

    private void requireNonNull(Object value, String field) {
        if (value == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, field + " is required");
        }
    }
}
