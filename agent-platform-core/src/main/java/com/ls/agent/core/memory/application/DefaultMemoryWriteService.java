package com.ls.agent.core.memory.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.regex.Pattern;

@Service
public class DefaultMemoryWriteService implements MemoryWriteService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_SUPERSEDED = "SUPERSEDED";
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final double SIMILARITY_THRESHOLD = 0.6;
    private static final int DEDUP_LOOKBACK = 5;
    private static final String MEMORY_STRATEGY_READ_WRITE = "READ_WRITE";
    private static final Pattern EXPLICIT_DO_NOT_REMEMBER = Pattern.compile(
            "(不要|别|不必|不用|无需).{0,8}(记住|保存|记录|写入)|" +
                    "(don't|do not|dont).{0,16}(remember|save|store)|" +
                    "(only|just).{0,12}(this|current).{0,12}(session|time)|" +
                    "(仅|只).{0,8}(本次|这次|当前会话).{0,8}(有效|使用)"
    );
    private static final Pattern EXPLICIT_FORGET = Pattern.compile(
            "(忘掉|忘记|删除.{0,8}记忆|不要再记住|别再记住)|" +
                    "(?i)(forget|remove|delete).{0,16}(memory|preference|fact|this|that)"
    );
    private static final Pattern SENSITIVE_CONTENT = Pattern.compile(
            "(?i)(" +
                    "api[_ -]?key|secret|password|passwd|token|bearer\\s+[a-z0-9._\\-]+|" +
                    "sk-[a-z0-9]{12,}|" +
                    "\\b\\d{15}(\\d{2}[0-9x])?\\b|" +
                    "\\b\\d{13,19}\\b|" +
                    "[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}" +
                    ")"
    );

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
        if (hasExplicitForgetIntent(command.content())) {
            disableRelatedMemories(command);
            return;
        }
        if (shouldSkipWrite(command)) {
            return;
        }
        String content = truncateContent(command.content());
        LocalDateTime now = LocalDateTime.now();
        List<MemoryEntity> candidates = findCandidateMemories(command);
        MemoryEntity superseded = findSupersededMemory(command, candidates);
        if (superseded != null) {
            markSuperseded(superseded);
        }

        // Check for existing similar memory to update instead of insert
        MemoryEntity existing = superseded == null ? findSimilarMemory(command, candidates) : null;
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
        entity.setMetadata(vectorStatusMetadata("PENDING", null, null, superseded == null ? null : superseded.getId()));
        memoryMapper.insert(entity);
        upsertVector(entity);
    }

    private void markSuperseded(MemoryEntity memory) {
        memory.setStatus(STATUS_SUPERSEDED);
        ObjectNode metadata = objectMetadata(memory.getMetadata());
        metadata.put("superseded_at", LocalDateTime.now().toString());
        memory.setMetadata(metadata);
        memoryMapper.updateById(memory);
        clearVector(memory);
    }

    private void upsertVector(MemoryEntity memory) {
        if (embeddingService == null || vectorStore == null || memory == null || memory.getId() == null) {
            return;
        }
        try {
            EmbeddingVectorDTO vector = embeddingService.embed(memory.getContent());
            if (vector == null || vector.dimension() == 0) {
                markVectorIndex(memory, "FAILED", null, "empty embedding vector");
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
            markVectorIndex(memory, "INDEXED", vector, null);
        } catch (RuntimeException ignored) {
            markVectorIndex(memory, "FAILED", null, errorMessage(ignored));
            // Vector indexing is a recall enhancement; PostgreSQL remains the source of truth.
        }
    }

    private void markVectorIndex(MemoryEntity memory, String status, EmbeddingVectorDTO vector, String errorMessage) {
        if (memory == null || memory.getId() == null) {
            return;
        }
        memory.setMetadata(vectorStatusMetadata(status, vector, errorMessage, metadataLong(memory.getMetadata(), "supersedes_memory_id")));
        memoryMapper.updateById(memory);
    }

    private JsonNode vectorStatusMetadata(String status, EmbeddingVectorDTO vector, String errorMessage, Long supersedesMemoryId) {
        ObjectNode metadata = JsonNodeFactory.instance.objectNode();
        metadata.put("source_type", "memory");
        metadata.put("vector_index_status", status);
        if (supersedesMemoryId != null) {
            metadata.put("supersedes_memory_id", supersedesMemoryId);
        }
        if (vector != null) {
            metadata.put("embedding_model", vector.model() == null ? "" : vector.model());
            metadata.put("embedding_dimension", vector.dimension());
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            metadata.put("vector_index_error", errorMessage);
        }
        return metadata;
    }

    private String errorMessage(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null) {
            return "";
        }
        return ex.getMessage().length() > 300 ? ex.getMessage().substring(0, 300) : ex.getMessage();
    }

    private List<MemoryEntity> findCandidateMemories(RecordMemoryCommand command) {
        List<MemoryEntity> memories = memoryMapper.selectList(new LambdaQueryWrapper<MemoryEntity>()
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
        return memories == null ? List.of() : memories;
    }

    /** Find an existing memory with similar content to avoid duplicates. */
    private MemoryEntity findSimilarMemory(RecordMemoryCommand command, List<MemoryEntity> recent) {
        if (recent == null || recent.isEmpty()) {
            return null;
        }
        for (MemoryEntity existing : recent) {
            if (isPinned(existing)) {
                continue;
            }
            if (contentSimilarity(existing.getContent(), command.content()) >= SIMILARITY_THRESHOLD) {
                return existing;
            }
        }
        return null;
    }

    private MemoryEntity findSupersededMemory(RecordMemoryCommand command, List<MemoryEntity> candidates) {
        String incomingKey = conflictKey(resolveCategory(command), tags(command.tags()));
        if (incomingKey == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (MemoryEntity candidate : candidates) {
            if (isPinned(candidate)) {
                continue;
            }
            String existingKey = conflictKey(resolveCategory(candidate), candidate.getTags());
            if (incomingKey.equals(existingKey) && contentSimilarity(candidate.getContent(), command.content()) < 0.95) {
                return candidate;
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
        requireNonNull(command, "command");
        requireNonNull(command.tenantId(), "tenantId");
        requireNonNull(command.userId(), "userId");
        if (command.memoryType() == null || command.memoryType().isBlank()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "memoryType is required");
        }
        if (command.content() == null || command.content().isBlank()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "content is required");
        }
    }

    private boolean shouldSkipWrite(RecordMemoryCommand command) {
        return !MEMORY_STRATEGY_READ_WRITE.equals(memoryStrategyMode(command))
                || hasExplicitDoNotRememberIntent(command.content())
                || containsSensitiveContent(command.content());
    }

    private String memoryStrategyMode(RecordMemoryCommand command) {
        String mode = command.memoryStrategyMode();
        if (mode == null || mode.isBlank()) {
            return MEMORY_STRATEGY_READ_WRITE;
        }
        return mode.strip().toUpperCase(Locale.ROOT);
    }

    private boolean hasExplicitDoNotRememberIntent(String content) {
        return content != null && EXPLICIT_DO_NOT_REMEMBER.matcher(content).find();
    }

    private boolean hasExplicitForgetIntent(String content) {
        return content != null && EXPLICIT_FORGET.matcher(content).find();
    }

    private boolean containsSensitiveContent(String content) {
        return content != null && SENSITIVE_CONTENT.matcher(content).find();
    }

    private void disableRelatedMemories(RecordMemoryCommand command) {
        List<String> keywords = forgetKeywords(command.content());
        LambdaQueryWrapper<MemoryEntity> wrapper = new LambdaQueryWrapper<MemoryEntity>()
                .eq(MemoryEntity::getTenantId, command.tenantId())
                .eq(MemoryEntity::getUserId, command.userId())
                .eq(MemoryEntity::getStatus, STATUS_ACTIVE)
                .and(w -> {
                    if (command.applicationId() == null) {
                        w.isNull(MemoryEntity::getApplicationId);
                    } else {
                        w.isNull(MemoryEntity::getApplicationId).or().eq(MemoryEntity::getApplicationId, command.applicationId());
                    }
                })
                .and(w -> {
                    if (command.profileId() == null) {
                        w.isNull(MemoryEntity::getProfileId);
                    } else {
                        w.isNull(MemoryEntity::getProfileId).or().eq(MemoryEntity::getProfileId, command.profileId());
                    }
                });
        if (!keywords.isEmpty()) {
            wrapper.and(w -> {
                for (int i = 0; i < keywords.size(); i++) {
                    if (i == 0) {
                        w.like(MemoryEntity::getContent, keywords.get(i));
                    } else {
                        w.or().like(MemoryEntity::getContent, keywords.get(i));
                    }
                }
            });
        }
        wrapper.last("limit 20");
        List<MemoryEntity> memories = memoryMapper.selectList(wrapper);
        if (memories == null || memories.isEmpty()) {
            return;
        }
        for (MemoryEntity memory : memories) {
            memory.setStatus(STATUS_DISABLED);
            memoryMapper.updateById(memory);
            clearVector(memory);
        }
    }

    private List<String> forgetKeywords(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(content.split("[^\\p{IsAlphabetic}\\p{IsDigit}_-]+"))
                .map(String::strip)
                .filter(value -> value.length() >= 3)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !List.of("forget", "remove", "delete", "memory", "preference", "fact").contains(value))
                .distinct()
                .limit(8)
                .toList();
    }

    private void clearVector(MemoryEntity memory) {
        if (vectorStore == null || memory == null || memory.getId() == null) {
            return;
        }
        try {
            vectorStore.deleteByDocument(
                    "memory",
                    memory.getTenantId(),
                    memory.getApplicationId(),
                    memory.getUserId(),
                    memory.getProfileId(),
                    memory.getId()
            );
        } catch (RuntimeException ignored) {
            // Forget/disable is anchored in PostgreSQL; vector cleanup can be retried later.
        }
    }

    private String resolveCategory(RecordMemoryCommand command) {
        if (command.memoryCategory() != null && !command.memoryCategory().isBlank()) {
            return command.memoryCategory().strip().toLowerCase(Locale.ROOT);
        }
        return command.memoryType().strip().toLowerCase(Locale.ROOT);
    }

    private String resolveCategory(MemoryEntity memory) {
        if (memory.getMemoryCategory() != null && !memory.getMemoryCategory().isBlank()) {
            return memory.getMemoryCategory().strip().toLowerCase(Locale.ROOT);
        }
        return memory.getMemoryType() == null ? "" : memory.getMemoryType().strip().toLowerCase(Locale.ROOT);
    }

    private String conflictKey(String category, String[] tags) {
        String normalizedCategory = category == null ? "" : category.strip().toLowerCase(Locale.ROOT);
        if (!"preference".equals(normalizedCategory) && !"fact".equals(normalizedCategory)) {
            return null;
        }
        return List.of(tags == null ? new String[0] : tags).stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> tag.strip().toLowerCase(Locale.ROOT))
                .filter(this::isStructuredConflictTag)
                .sorted()
                .findFirst()
                .map(tag -> normalizedCategory + ":" + tag)
                .orElse(null);
    }

    private boolean isStructuredConflictTag(String tag) {
        return tag.startsWith("pref:")
                || tag.startsWith("key:")
                || tag.endsWith("_style")
                || tag.endsWith("_preference")
                || tag.endsWith("_fact");
    }

    private boolean isPinned(MemoryEntity memory) {
        return memory != null && memory.getMetadata() != null && memory.getMetadata().path("pinned").asBoolean(false);
    }

    private ObjectNode objectMetadata(JsonNode metadata) {
        if (metadata instanceof ObjectNode objectNode) {
            return objectNode.deepCopy();
        }
        return JsonNodeFactory.instance.objectNode();
    }

    private Long metadataLong(JsonNode metadata, String field) {
        if (metadata == null || !metadata.hasNonNull(field)) {
            return null;
        }
        long value = metadata.path(field).asLong(0L);
        return value <= 0 ? null : value;
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
