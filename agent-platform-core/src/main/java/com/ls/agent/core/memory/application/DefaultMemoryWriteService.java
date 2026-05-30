package com.ls.agent.core.memory.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.memory.api.MemoryWriteService;
import com.ls.agent.core.memory.command.RecordMemoryCommand;
import com.ls.agent.core.memory.entity.MemoryEntity;
import com.ls.agent.core.memory.mapper.MemoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultMemoryWriteService implements MemoryWriteService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final double SIMILARITY_THRESHOLD = 0.6;
    private static final int DEDUP_LOOKBACK = 5;

    private final MemoryMapper memoryMapper;

    public DefaultMemoryWriteService(MemoryMapper memoryMapper) {
        this.memoryMapper = memoryMapper;
    }

    @Override
    public void record(RecordMemoryCommand command) {
        validate(command);
        String content = truncateContent(command.content());

        // Check for existing similar memory to update instead of insert
        MemoryEntity existing = findSimilarMemory(command);
        if (existing != null) {
            existing.setContent(existing.getContent() + "\n" + content);
            if (existing.getContent().length() > MAX_CONTENT_LENGTH * 2) {
                existing.setContent(existing.getContent().substring(0, MAX_CONTENT_LENGTH * 2));
            }
            existing.setMemoryType(command.memoryType());
            existing.setSourceConversationId(command.sourceConversationId());
            memoryMapper.updateById(existing);
            return;
        }

        MemoryEntity entity = new MemoryEntity();
        entity.setTenantId(command.tenantId());
        entity.setUserId(command.userId());
        entity.setApplicationId(command.applicationId());
        entity.setProfileId(command.profileId());
        entity.setMemoryType(command.memoryType());
        entity.setContent(content);
        entity.setSourceConversationId(command.sourceConversationId());
        entity.setStatus(STATUS_ACTIVE);
        memoryMapper.insert(entity);
    }

    /** Find an existing memory with similar content to avoid duplicates. */
    private MemoryEntity findSimilarMemory(RecordMemoryCommand command) {
        List<MemoryEntity> recent = memoryMapper.selectList(new LambdaQueryWrapper<MemoryEntity>()
                .eq(MemoryEntity::getTenantId, command.tenantId())
                .eq(MemoryEntity::getUserId, command.userId())
                .eq(MemoryEntity::getStatus, STATUS_ACTIVE)
                .eq(MemoryEntity::getMemoryType, command.memoryType())
                .and(w -> {
                    if (command.applicationId() == null) {
                        w.isNull(MemoryEntity::getApplicationId);
                    } else {
                        w.eq(MemoryEntity::getApplicationId, command.applicationId());
                    }
                })
                .and(w -> {
                    if (command.profileId() == null) {
                        w.isNull(MemoryEntity::getProfileId);
                    } else {
                        w.eq(MemoryEntity::getProfileId, command.profileId());
                    }
                })
                .orderByDesc(MemoryEntity::getUpdatedAt)
                .last("limit " + DEDUP_LOOKBACK));

        for (MemoryEntity existing : recent) {
            if (contentSimilarity(existing.getContent(), command.content()) >= SIMILARITY_THRESHOLD) {
                return existing;
            }
        }
        return null;
    }

    /** Simple Jaccard-like similarity based on word overlap. */
    private double contentSimilarity(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return 0;
        }
        String[] wordsA = a.split("\\s+");
        String[] wordsB = b.split("\\s+");
        if (wordsA.length == 0 || wordsB.length == 0) {
            return 0;
        }
        int overlap = 0;
        for (String wa : wordsA) {
            for (String wb : wordsB) {
                if (wa.equalsIgnoreCase(wb)) {
                    overlap++;
                    break;
                }
            }
        }
        return (double) overlap / Math.max(wordsA.length, wordsB.length);
    }

    private String truncateContent(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= MAX_CONTENT_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_CONTENT_LENGTH) + "...";
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
