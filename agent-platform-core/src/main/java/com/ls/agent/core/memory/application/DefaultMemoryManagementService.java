package com.ls.agent.core.memory.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.memory.api.MemoryManagementService;
import com.ls.agent.core.memory.command.UpdateMemoryCommand;
import com.ls.agent.core.memory.dto.MemoryRecordDTO;
import com.ls.agent.core.memory.entity.MemoryEntity;
import com.ls.agent.core.memory.mapper.MemoryMapper;
import com.ls.agent.core.rag.api.EmbeddingService;
import com.ls.agent.core.rag.api.VectorStore;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;
import com.ls.agent.core.rag.dto.VectorStoreDocumentDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class DefaultMemoryManagementService implements MemoryManagementService {

    private static final String SOURCE_TYPE_MEMORY = "memory";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final MemoryMapper memoryMapper;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public DefaultMemoryManagementService(MemoryMapper memoryMapper) {
        this(memoryMapper, null, null);
    }

    @Autowired
    public DefaultMemoryManagementService(
            MemoryMapper memoryMapper,
            EmbeddingService embeddingService,
            VectorStore vectorStore
    ) {
        this.memoryMapper = memoryMapper;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    @Override
    public List<MemoryRecordDTO> list(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String category,
            String query,
            int limit
    ) {
        requireNonNull(tenantId, "tenantId");
        requireNonNull(userId, "userId");
        LambdaQueryWrapper<MemoryEntity> wrapper = new LambdaQueryWrapper<MemoryEntity>()
                .eq(MemoryEntity::getTenantId, tenantId)
                .eq(MemoryEntity::getUserId, userId)
                .eq(MemoryEntity::getStatus, STATUS_ACTIVE);
        appendApplicationScope(wrapper, applicationId);
        appendProfileScope(wrapper, profileId);
        if (category != null && !category.isBlank()) {
            wrapper.eq(MemoryEntity::getMemoryCategory, normalize(category));
        }
        if (query != null && !query.isBlank()) {
            wrapper.like(MemoryEntity::getContent, query.strip());
        }
        wrapper.orderByDesc(MemoryEntity::getUpdatedAt)
                .orderByDesc(MemoryEntity::getId)
                .last("limit " + clampLimit(limit));
        return memoryMapper.selectList(wrapper).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public MemoryRecordDTO update(UpdateMemoryCommand command) {
        validateUpdate(command);
        MemoryEntity memory = scopedActiveMemory(
                command.tenantId(),
                command.applicationId(),
                command.userId(),
                command.profileId(),
                command.memoryId()
        );
        if (memory == null) {
            return null;
        }
        boolean reindex = false;
        if (command.content() != null && !command.content().isBlank()) {
            String content = command.content().strip();
            reindex = !content.equals(memory.getContent());
            memory.setContent(content);
        }
        if (command.memoryCategory() != null && !command.memoryCategory().isBlank()) {
            memory.setMemoryCategory(normalize(command.memoryCategory()));
        }
        if (command.tags() != null) {
            memory.setTags(tags(command.tags()));
        }
        if (command.importance() != null) {
            memory.setImportance(clamp(command.importance()));
        }
        if (command.slotHint() != null) {
            memory.setSlotHint(command.slotHint().isBlank() ? null : command.slotHint().strip());
        }
        memoryMapper.updateById(memory);
        if (reindex) {
            upsertVector(memory);
        }
        return toDTO(memory);
    }

    @Override
    public int disable(Long tenantId, Long applicationId, Long userId, Long profileId, Long memoryId) {
        requireNonNull(tenantId, "tenantId");
        requireNonNull(userId, "userId");
        requireNonNull(memoryId, "memoryId");
        MemoryEntity memory = scopedActiveMemory(tenantId, applicationId, userId, profileId, memoryId);
        if (memory == null) {
            return 0;
        }
        memory.setStatus(STATUS_DISABLED);
        int changed = memoryMapper.updateById(memory);
        clearVector(memory);
        return changed <= 0 ? 1 : changed;
    }

    private MemoryEntity scopedActiveMemory(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            Long memoryId
    ) {
        MemoryEntity memory = memoryMapper.selectById(memoryId);
        if (memory == null || !STATUS_ACTIVE.equals(memory.getStatus())) {
            return null;
        }
        if (!tenantId.equals(memory.getTenantId()) || !userId.equals(memory.getUserId())) {
            return null;
        }
        if (!visibleInScope(applicationId, memory.getApplicationId()) || !visibleInScope(profileId, memory.getProfileId())) {
            return null;
        }
        return memory;
    }

    private void appendApplicationScope(LambdaQueryWrapper<MemoryEntity> wrapper, Long applicationId) {
        if (applicationId == null) {
            wrapper.isNull(MemoryEntity::getApplicationId);
            return;
        }
        wrapper.and(w -> w.isNull(MemoryEntity::getApplicationId)
                .or()
                .eq(MemoryEntity::getApplicationId, applicationId));
    }

    private void appendProfileScope(LambdaQueryWrapper<MemoryEntity> wrapper, Long profileId) {
        if (profileId == null) {
            wrapper.isNull(MemoryEntity::getProfileId);
            return;
        }
        wrapper.and(w -> w.isNull(MemoryEntity::getProfileId)
                .or()
                .eq(MemoryEntity::getProfileId, profileId));
    }

    private boolean visibleInScope(Long requested, Long actual) {
        if (actual == null) {
            return true;
        }
        return requested != null && requested.equals(actual);
    }

    private void upsertVector(MemoryEntity memory) {
        if (embeddingService == null || vectorStore == null || memory.getId() == null) {
            return;
        }
        try {
            EmbeddingVectorDTO vector = embeddingService.embed(memory.getContent());
            if (vector == null || vector.dimension() == 0) {
                return;
            }
            vectorStore.upsert(new VectorStoreDocumentDTO(
                    SOURCE_TYPE_MEMORY,
                    "memory-" + memory.getId(),
                    memory.getTenantId(),
                    memory.getApplicationId(),
                    memory.getUserId(),
                    memory.getProfileId(),
                    memory.getId(),
                    memory.getId(),
                    vector
            ));
        } catch (RuntimeException ignored) {
            // Vector index is derived data; PostgreSQL remains the memory source of truth.
        }
    }

    private void clearVector(MemoryEntity memory) {
        if (vectorStore == null || memory.getId() == null) {
            return;
        }
        try {
            vectorStore.deleteByDocument(
                    SOURCE_TYPE_MEMORY,
                    memory.getTenantId(),
                    memory.getApplicationId(),
                    memory.getUserId(),
                    memory.getProfileId(),
                    memory.getId()
            );
        } catch (RuntimeException ignored) {
            // Manual disable must not fail just because the vector index is temporarily unavailable.
        }
    }

    private MemoryRecordDTO toDTO(MemoryEntity memory) {
        return new MemoryRecordDTO(
                memory.getId(),
                memory.getApplicationId(),
                memory.getProfileId(),
                memory.getMemoryType(),
                memory.getMemoryCategory(),
                memory.getContent(),
                memory.getTags() == null ? List.of() : Arrays.asList(memory.getTags()),
                memory.getImportance() == null ? 0.5 : memory.getImportance(),
                memory.getConfidence() == null ? 0.0 : memory.getConfidence(),
                memory.getAccessCount() == null ? 0 : memory.getAccessCount(),
                memory.getLastAccessedAt(),
                memory.getCreatedAt(),
                memory.getUpdatedAt(),
                memory.getSlotHint(),
                memory.getStatus()
        );
    }

    private void validateUpdate(UpdateMemoryCommand command) {
        requireNonNull(command, "command");
        requireNonNull(command.tenantId(), "tenantId");
        requireNonNull(command.userId(), "userId");
        requireNonNull(command.memoryId(), "memoryId");
    }

    private void requireNonNull(Object value, String field) {
        if (value == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, field + " is required");
        }
    }

    private int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private String[] tags(List<String> tags) {
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::strip)
                .distinct()
                .toArray(String[]::new);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
