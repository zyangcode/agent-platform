package com.ls.agent.core.memory.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.memory.api.MemoryWriteService;
import com.ls.agent.core.memory.command.RecordMemoryCommand;
import com.ls.agent.core.memory.entity.MemoryEntity;
import com.ls.agent.core.memory.mapper.MemoryMapper;
import com.ls.agent.core.rag.api.EmbeddingService;
import com.ls.agent.core.rag.api.VectorStore;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;
import com.ls.agent.core.rag.dto.VectorStoreDocumentDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class DefaultMemoryWriteService implements MemoryWriteService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final double SIMILARITY_THRESHOLD = 0.6;
    private static final int DEDUP_LOOKBACK = 5;

    private final MemoryMapper memoryMapper;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public DefaultMemoryWriteService(MemoryMapper memoryMapper) {
        this(memoryMapper, null, null);
    }

    @Autowired
    public DefaultMemoryWriteService(
            MemoryMapper memoryMapper,
            EmbeddingService embeddingService,
            VectorStore vectorStore
    ) {
        this.memoryMapper = memoryMapper;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    @Override
    public void record(RecordMemoryCommand command) {
        validate(command);
        String content = truncateContent(command.content());
        LocalDateTime now = LocalDateTime.now();

        // Check for existing similar memory to update instead of insert
        MemoryEntity existing = findSimilarMemory(command);
        if (existing != null) {
            existing.setContent(existing.getContent() + "\n" + content);
            if (existing.getContent().length() > MAX_CONTENT_LENGTH * 2) {
                existing.setContent(existing.getContent().substring(0, MAX_CONTENT_LENGTH * 2));
            }
            existing.setMemoryType(command.memoryType());
            existing.setMemoryCategory(resolveCategory(command));
            existing.setTags(tags(command.tags()));
            existing.setImportance(Math.max(resolveImportance(existing.getImportance()), resolveImportance(command.importance())));
            existing.setConfidence(resolveConfidence(existing.getConfidence()));
            existing.setAccessCount(nextAccessCount(existing.getAccessCount()));
            existing.setLastAccessedAt(now);
            existing.setSlotHint(command.slotHint());
            existing.setSourceConversationId(command.sourceConversationId());
            memoryMapper.updateById(existing);
            upsertVector(existing);
            return;
        }

        MemoryEntity entity = new MemoryEntity();
        entity.setTenantId(command.tenantId());
        entity.setUserId(command.userId());
        entity.setApplicationId(command.applicationId());
        entity.setProfileId(command.profileId());
        entity.setMemoryType(command.memoryType());
        entity.setMemoryCategory(resolveCategory(command));
        entity.setContent(content);
        entity.setTags(tags(command.tags()));
        entity.setImportance(resolveImportance(command.importance()));
        entity.setConfidence(resolveConfidence(null));
        entity.setAccessCount(0);
        entity.setLastAccessedAt(now);
        entity.setSlotHint(command.slotHint());
        entity.setSourceConversationId(command.sourceConversationId());
        entity.setStatus(STATUS_ACTIVE);
        memoryMapper.insert(entity);
        upsertVector(entity);
    }

    private void upsertVector(MemoryEntity memory) {
        if (embeddingService == null || vectorStore == null || memory == null || memory.getId() == null) {
            return;
        }
        try {
            EmbeddingVectorDTO vector = embeddingService.embed(memory.getContent());
            if (vector == null || vector.dimension() == 0) {
                return;
            }
            vectorStore.upsert(new VectorStoreDocumentDTO(
                    "memory",
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
            // Vector indexing is a recall enhancement; PostgreSQL remains the source of truth.
        }
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

        if (recent == null || recent.isEmpty()) {
            return null;
        }
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

    private String resolveCategory(RecordMemoryCommand command) {
        if (command.memoryCategory() != null && !command.memoryCategory().isBlank()) {
            return command.memoryCategory().strip().toLowerCase(Locale.ROOT);
        }
        return command.memoryType().strip().toLowerCase(Locale.ROOT);
    }

    private String[] tags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new String[0];
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::strip)
                .distinct()
                .toArray(String[]::new);
    }

    private double resolveImportance(Double importance) {
        if (importance == null) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0, importance));
    }

    private double resolveConfidence(Double confidence) {
        if (confidence == null) {
            return 0.8;
        }
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private int nextAccessCount(Integer accessCount) {
        return accessCount == null ? 1 : accessCount + 1;
    }

    private void requireNonNull(Object value, String field) {
        if (value == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, field + " is required");
        }
    }
}
