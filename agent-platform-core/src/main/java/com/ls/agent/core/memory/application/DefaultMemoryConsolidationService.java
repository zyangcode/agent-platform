package com.ls.agent.core.memory.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ls.agent.core.memory.dto.MemoryConsolidationResult;
import com.ls.agent.core.memory.entity.MemoryEntity;
import com.ls.agent.core.memory.mapper.MemoryMapper;
import com.ls.agent.core.rag.api.VectorStore;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.FinishTraceSpanCommand;
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceSpanDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class DefaultMemoryConsolidationService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String VECTOR_SOURCE_MEMORY = "memory";
    private static final int STALE_DAYS = 30;
    private static final double DECAY_RATE = 0.85;
    private static final int MAX_SCAN_LIMIT = 200;

    private final MemoryMapper memoryMapper;
    private final TraceService traceService;
    private final VectorStore vectorStore;

    public DefaultMemoryConsolidationService(MemoryMapper memoryMapper) {
        this(memoryMapper, null, null);
    }

    @Autowired
    public DefaultMemoryConsolidationService(
            MemoryMapper memoryMapper,
            TraceService traceService,
            VectorStore vectorStore
    ) {
        this.memoryMapper = memoryMapper;
        this.traceService = traceService;
        this.vectorStore = vectorStore;
    }

    public MemoryConsolidationResult consolidate(Long tenantId, Long userId, Long applicationId, Long profileId) {
        return consolidate(tenantId, userId, applicationId, profileId, null, null);
    }

    public MemoryConsolidationResult consolidate(
            Long tenantId,
            Long userId,
            Long applicationId,
            Long profileId,
            String traceId,
            Long parentSpanId
    ) {
        ObjectNode attributes = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                .put("tenantId", tenantId == null ? 0 : tenantId)
                .put("userId", userId == null ? 0 : userId)
                .put("applicationId", applicationId == null ? 0 : applicationId)
                .put("profileId", profileId == null ? 0 : profileId)
                .put("scanLimit", MAX_SCAN_LIMIT);
        TraceSpanDTO span = safeStartSpan(traceId, parentSpanId, attributes);
        try {
            MemoryConsolidationResult result = doConsolidate(tenantId, userId, applicationId, profileId);
            attributes.put("scannedCount", result.scannedCount());
            attributes.put("expiredCount", result.expiredCount());
            attributes.put("decayedCount", result.decayedCount());
            attributes.put("mergedCount", result.mergedCount());
            safeFinishSpan(span, "SUCCESS", null, null);
            return result;
        } catch (Exception ex) {
            safeFinishSpan(span, "FAILED", ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    private MemoryConsolidationResult doConsolidate(Long tenantId, Long userId, Long applicationId, Long profileId) {
        List<MemoryEntity> memories = memoryMapper.selectList(baseWrapper(tenantId, userId, applicationId, profileId)
                .orderByDesc(MemoryEntity::getUpdatedAt)
                .last("limit " + MAX_SCAN_LIMIT));
        if (memories == null || memories.isEmpty()) {
            return new MemoryConsolidationResult(0, 0, 0, 0);
        }

        LocalDateTime now = LocalDateTime.now();
        List<MemoryEntity> active = new ArrayList<>();
        int expiredCount = 0;
        for (MemoryEntity memory : memories) {
            if (isExpired(memory, now)) {
                memoryMapper.deleteById(memory.getId());
                deleteMemoryVector(memory);
                expiredCount++;
            } else {
                active.add(memory);
            }
        }

        int mergedCount = mergeDuplicates(active);
        int decayedCount = decayStaleMemories(active, now);
        return new MemoryConsolidationResult(memories.size(), expiredCount, decayedCount, mergedCount);
    }

    private TraceSpanDTO safeStartSpan(String traceId, Long parentSpanId, ObjectNode attributes) {
        if (traceService == null || traceId == null || traceId.isBlank()) {
            return null;
        }
        try {
            return traceService.startSpan(new StartTraceSpanCommand(
                    traceId,
                    parentSpanId,
                    "memory.consolidate",
                    "MEMORY",
                    "core",
                    attributes
            ));
        } catch (Exception ex) {
            return null;
        }
    }

    private void safeFinishSpan(TraceSpanDTO span, String status, String errorCode, String errorMessage) {
        if (traceService == null || span == null || span.id() == null) {
            return;
        }
        try {
            traceService.finishSpan(new FinishTraceSpanCommand(span.id(), status, errorCode, errorMessage, span.attributes()));
        } catch (Exception ex) {
            // Trace is diagnostic data; it must not break memory consolidation.
        }
    }

    private LambdaQueryWrapper<MemoryEntity> baseWrapper(Long tenantId, Long userId, Long applicationId, Long profileId) {
        LambdaQueryWrapper<MemoryEntity> wrapper = new LambdaQueryWrapper<MemoryEntity>()
                .eq(MemoryEntity::getTenantId, tenantId)
                .eq(MemoryEntity::getUserId, userId)
                .eq(MemoryEntity::getStatus, STATUS_ACTIVE);
        if (applicationId == null) {
            wrapper.isNull(MemoryEntity::getApplicationId);
        } else {
            wrapper.eq(MemoryEntity::getApplicationId, applicationId);
        }
        if (profileId == null) {
            wrapper.isNull(MemoryEntity::getProfileId);
        } else {
            wrapper.eq(MemoryEntity::getProfileId, profileId);
        }
        return wrapper;
    }

    private boolean isExpired(MemoryEntity memory, LocalDateTime now) {
        return memory.getExpiresAt() != null && memory.getExpiresAt().isBefore(now);
    }

    private int mergeDuplicates(List<MemoryEntity> memories) {
        int merged = 0;
        Set<Long> deletedIds = new LinkedHashSet<>();
        for (int i = 0; i < memories.size(); i++) {
            MemoryEntity keeper = memories.get(i);
            if (keeper.getId() != null && deletedIds.contains(keeper.getId())) {
                continue;
            }
            for (int j = i + 1; j < memories.size(); j++) {
                MemoryEntity candidate = memories.get(j);
                if (candidate.getId() != null && deletedIds.contains(candidate.getId())) {
                    continue;
                }
                if (isPinned(keeper) || isPinned(candidate)) {
                    continue;
                }
                if (!sameKind(keeper, candidate) || !isDuplicateContent(keeper.getContent(), candidate.getContent())) {
                    continue;
                }
                mergeInto(keeper, candidate);
                memoryMapper.updateById(keeper);
                memoryMapper.deleteById(candidate.getId());
                deleteMemoryVector(candidate);
                if (candidate.getId() != null) {
                    deletedIds.add(candidate.getId());
                }
                merged++;
            }
        }
        memories.removeIf(memory -> memory.getId() != null && deletedIds.contains(memory.getId()));
        return merged;
    }

    private int decayStaleMemories(List<MemoryEntity> memories, LocalDateTime now) {
        int decayed = 0;
        LocalDateTime staleBefore = now.minusDays(STALE_DAYS);
        for (MemoryEntity memory : memories) {
            if (isPinned(memory)) {
                continue;
            }
            LocalDateTime accessedAt = memory.getLastAccessedAt();
            if (accessedAt == null || !accessedAt.isBefore(staleBefore)) {
                continue;
            }
            double importance = memory.getImportance() == null ? 0.5 : memory.getImportance();
            memory.setImportance(Math.max(0.0, importance * DECAY_RATE));
            memoryMapper.updateById(memory);
            decayed++;
        }
        return decayed;
    }

    private boolean sameKind(MemoryEntity first, MemoryEntity second) {
        return normalize(first.getMemoryType()).equals(normalize(second.getMemoryType()))
                && normalize(category(first)).equals(normalize(category(second)));
    }

    private String category(MemoryEntity memory) {
        if (memory.getMemoryCategory() != null && !memory.getMemoryCategory().isBlank()) {
            return memory.getMemoryCategory();
        }
        return memory.getMemoryType();
    }

    private boolean isDuplicateContent(String first, String second) {
        String a = normalize(first);
        String b = normalize(second);
        if (a.isBlank() || b.isBlank()) {
            return false;
        }
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    private void mergeInto(MemoryEntity keeper, MemoryEntity candidate) {
        keeper.setImportance(Math.max(valueOrDefault(keeper.getImportance(), 0.5), valueOrDefault(candidate.getImportance(), 0.5)));
        keeper.setConfidence(Math.max(valueOrDefault(keeper.getConfidence(), 0.8), valueOrDefault(candidate.getConfidence(), 0.8)));
        keeper.setAccessCount(valueOrDefault(keeper.getAccessCount(), 0) + valueOrDefault(candidate.getAccessCount(), 0));
        if (keeper.getLastAccessedAt() == null || newer(candidate.getLastAccessedAt(), keeper.getLastAccessedAt())) {
            keeper.setLastAccessedAt(candidate.getLastAccessedAt());
        }
        keeper.setTags(mergeTags(keeper.getTags(), candidate.getTags()));
    }

    private String[] mergeTags(String[] first, String[] second) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        addTags(merged, first);
        addTags(merged, second);
        return merged.toArray(String[]::new);
    }

    private void addTags(Set<String> target, String[] tags) {
        if (tags == null) {
            return;
        }
        Arrays.stream(tags)
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::strip)
                .forEach(target::add);
    }

    private boolean newer(LocalDateTime candidate, LocalDateTime current) {
        return candidate != null && candidate.isAfter(current);
    }

    private double valueOrDefault(Double value, double defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private boolean isPinned(MemoryEntity memory) {
        return memory != null
                && memory.getMetadata() != null
                && memory.getMetadata().path("pinned").asBoolean(false);
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private void deleteMemoryVector(MemoryEntity memory) {
        if (vectorStore == null || memory == null || memory.getId() == null) {
            return;
        }
        try {
            vectorStore.deleteByDocument(
                    VECTOR_SOURCE_MEMORY,
                    memory.getTenantId(),
                    memory.getApplicationId(),
                    memory.getUserId(),
                    memory.getProfileId(),
                    memory.getId()
            );
        } catch (RuntimeException ignored) {
            // PostgreSQL memory state is authoritative; vector cleanup can be retried later.
        }
    }
}
