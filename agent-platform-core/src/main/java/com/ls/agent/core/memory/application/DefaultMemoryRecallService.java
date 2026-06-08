package com.ls.agent.core.memory.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ls.agent.core.memory.api.MemoryRecallService;
import com.ls.agent.core.memory.dto.MemoryDTO;
import com.ls.agent.core.memory.dto.MemoryRecallFilter;
import com.ls.agent.core.memory.entity.MemoryEntity;
import com.ls.agent.core.memory.mapper.MemoryMapper;
import com.ls.agent.core.rag.api.EmbeddingService;
import com.ls.agent.core.rag.api.VectorStore;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;
import com.ls.agent.core.rag.dto.VectorSearchQueryDTO;
import com.ls.agent.core.rag.dto.VectorSearchResultDTO;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.FinishTraceSpanCommand;
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceSpanDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DefaultMemoryRecallService implements MemoryRecallService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int FETCH_MULTIPLIER = 10;
    private static final Pattern KEYWORD_SPLITTER = Pattern.compile(
            "[\\s,./;:'\"\\[\\]{}|_=+\\-!@#$%^&*()"
                    + "，。！？；：、“”‘’]+");
    private static final int MIN_KEYWORD_LENGTH = 2;

    private final MemoryMapper memoryMapper;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final TraceService traceService;

    public DefaultMemoryRecallService(MemoryMapper memoryMapper) {
        this(memoryMapper, null, null, null);
    }

    public DefaultMemoryRecallService(
            MemoryMapper memoryMapper,
            EmbeddingService embeddingService,
            VectorStore vectorStore
    ) {
        this(memoryMapper, embeddingService, vectorStore, null);
    }

    @Autowired
    public DefaultMemoryRecallService(
            MemoryMapper memoryMapper,
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            TraceService traceService
    ) {
        this.memoryMapper = memoryMapper;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.traceService = traceService;
    }

    @Override
    public List<MemoryDTO> recall(Long tenantId, Long applicationId, Long userId, Long profileId, String query, int limit) {
        return recall(tenantId, applicationId, userId, profileId, query,
                MemoryRecallFilter.builder().topK(limit).build());
    }

    @Override
    public List<MemoryDTO> recall(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            MemoryRecallFilter filter
    ) {
        return recall(tenantId, applicationId, userId, profileId, query, filter, null, null);
    }

    @Override
    public List<MemoryDTO> recall(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            MemoryRecallFilter filter,
            String traceId,
            Long parentSpanId
    ) {
        MemoryRecallFilter resolvedFilter = filter == null
                ? MemoryRecallFilter.builder().build()
                : filter;
        int limit = resolvedFilter.resolvedTopK(5);
        List<MemoryEntity> vectorMemories = recallByVector(
                tenantId,
                applicationId,
                userId,
                profileId,
                query,
                resolvedFilter,
                limit,
                traceId,
                parentSpanId
        );
        if (!vectorMemories.isEmpty()) {
            touchReturnedMemories(vectorMemories);
            return vectorMemories.stream()
                    .map(this::toDTO)
                    .toList();
        }
        List<String> keywords = extractKeywords(query);
        LambdaQueryWrapper<MemoryEntity> wrapper = baseWrapper(tenantId, applicationId, userId, profileId);

        // Add keyword-based filtering if keywords exist
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

        wrapper.orderByDesc(MemoryEntity::getUpdatedAt)
                .last("limit " + Math.max(1, limit * FETCH_MULTIPLIER));

        List<MemoryEntity> candidates = memoryMapper.selectList(wrapper);
        if ((candidates == null || candidates.isEmpty()) && !keywords.isEmpty()) {
            candidates = memoryMapper.selectList(baseWrapper(tenantId, applicationId, userId, profileId)
                    .orderByDesc(MemoryEntity::getUpdatedAt)
                    .last("limit " + Math.max(1, limit * FETCH_MULTIPLIER)));
        }
        if (candidates == null) {
            candidates = List.of();
        }

        LocalDateTime minUpdatedAt = resolvedFilter.maxAge() == null
                ? null
                : LocalDateTime.now().minus(resolvedFilter.maxAge());
        List<MemoryEntity> scored = candidates.stream()
                .filter(memory -> matchesFilter(memory, resolvedFilter, minUpdatedAt))
                .filter(memory -> score(memory, keywords) >= minScore(resolvedFilter))
                .sorted(Comparator
                        .<MemoryEntity>comparingDouble(m -> score(m, keywords))
                        .reversed()
                        .thenComparing(MemoryEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<MemoryEntity> returned = collapseConflicts(scored).stream()
                .limit(Math.max(1, limit))
                .toList();
        touchReturnedMemories(returned);
        return returned.stream()
                .map(this::toDTO)
                .toList();
    }

    private List<MemoryEntity> recallByVector(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            MemoryRecallFilter filter,
            int limit,
            String traceId,
            Long parentSpanId
    ) {
        if (embeddingService == null || vectorStore == null || query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        try {
            TraceSpanDTO embeddingSpan = safeStartSpan(traceId, parentSpanId, "memory.embedding", attributes().put("queryChars", query.length()));
            EmbeddingVectorDTO queryVector;
            try {
                queryVector = embeddingService.embed(query);
                if (embeddingSpan != null && embeddingSpan.attributes() instanceof com.fasterxml.jackson.databind.node.ObjectNode attributes) {
                    attributes.put("model", queryVector == null ? "" : queryVector.model());
                    attributes.put("dimension", queryVector == null ? 0 : queryVector.dimension());
                }
                safeFinishSpan(embeddingSpan, "SUCCESS", null, null);
            } catch (RuntimeException ex) {
                safeFinishSpan(embeddingSpan, "FAILED", errorCode(ex), errorMessage(ex));
                throw ex;
            }
            if (queryVector == null || queryVector.dimension() == 0) {
                return List.of();
            }
            TraceSpanDTO vectorSpan = safeStartSpan(traceId, parentSpanId, "memory.vector.search",
                    attributes()
                            .put("topK", Math.max(1, limit * FETCH_MULTIPLIER))
                            .put("dimension", queryVector.dimension())
                            .put("profileScoped", profileId != null));
            List<VectorSearchResultDTO> vectorResults;
            try {
                vectorResults = vectorStore.search(new VectorSearchQueryDTO(
                        "memory",
                        tenantId,
                        applicationId,
                        userId,
                        profileId,
                        queryVector,
                        Math.max(1, limit * FETCH_MULTIPLIER)
                ));
                if (vectorSpan != null && vectorSpan.attributes() instanceof com.fasterxml.jackson.databind.node.ObjectNode attributes) {
                    attributes.put("resultCount", vectorResults == null ? 0 : vectorResults.size());
                }
                safeFinishSpan(vectorSpan, "SUCCESS", null, null);
            } catch (RuntimeException ex) {
                safeFinishSpan(vectorSpan, "FAILED", errorCode(ex), errorMessage(ex));
                throw ex;
            }
            if (vectorResults == null || vectorResults.isEmpty()) {
                return List.of();
            }
            Map<Long, Double> scoresById = vectorResults.stream()
                    .filter(result -> result.documentId() != null)
                    .collect(Collectors.toMap(
                            VectorSearchResultDTO::documentId,
                            VectorSearchResultDTO::score,
                            Math::max,
                            LinkedHashMap::new
                    ));
            if (scoresById.isEmpty()) {
                return List.of();
            }
            List<MemoryEntity> memories = memoryMapper.selectBatchIds(scoresById.keySet());
            if (memories == null || memories.isEmpty()) {
                return List.of();
            }
            LocalDateTime minUpdatedAt = filter.maxAge() == null
                    ? null
                    : LocalDateTime.now().minus(filter.maxAge());
            return memories.stream()
                    .filter(memory -> matchesScope(memory, tenantId, applicationId, userId, profileId))
                    .filter(memory -> matchesFilter(memory, filter, minUpdatedAt))
                    .filter(memory -> scoresById.getOrDefault(memory.getId(), 0.0) >= minScore(filter))
                    .sorted(Comparator
                            .<MemoryEntity>comparingDouble(memory -> scoresById.getOrDefault(memory.getId(), 0.0)
                                    + Math.max(0.0, Math.min(1.0, memory.getImportance() == null ? 0.5 : memory.getImportance())))
                            .reversed()
                            .thenComparing(MemoryEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.collectingAndThen(Collectors.toList(), this::collapseConflicts))
                    .stream()
                    .limit(Math.max(1, limit))
                    .toList();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private com.fasterxml.jackson.databind.node.ObjectNode attributes() {
        return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    }

    private TraceSpanDTO safeStartSpan(
            String traceId,
            Long parentSpanId,
            String spanName,
            com.fasterxml.jackson.databind.JsonNode attributes
    ) {
        if (traceService == null || traceId == null || traceId.isBlank()) {
            return null;
        }
        try {
            return traceService.startSpan(new StartTraceSpanCommand(
                    traceId,
                    parentSpanId,
                    spanName,
                    "MEMORY",
                    "core.memory",
                    attributes
            ));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void safeFinishSpan(TraceSpanDTO span, String status, String errorCode, String errorMessage) {
        if (traceService == null || span == null || span.id() == null) {
            return;
        }
        try {
            traceService.finishSpan(new FinishTraceSpanCommand(span.id(), status, errorCode, errorMessage, span.attributes()));
        } catch (RuntimeException ex) {
            // Trace is diagnostic data; it must not break memory recall.
        }
    }

    private String errorCode(RuntimeException ex) {
        return ex == null ? null : ex.getClass().getSimpleName();
    }

    private String errorMessage(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null) {
            return null;
        }
        return ex.getMessage().length() > 500 ? ex.getMessage().substring(0, 500) : ex.getMessage();
    }

    private boolean matchesScope(
            MemoryEntity memory,
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId
    ) {
        if (memory == null || !Objects.equals(memory.getTenantId(), tenantId) || !Objects.equals(memory.getUserId(), userId)) {
            return false;
        }
        boolean applicationMatches = memory.getApplicationId() == null || Objects.equals(memory.getApplicationId(), applicationId);
        boolean profileMatches = memory.getProfileId() == null || Objects.equals(memory.getProfileId(), profileId);
        return applicationMatches && profileMatches && STATUS_ACTIVE.equals(memory.getStatus());
    }

    private LambdaQueryWrapper<MemoryEntity> baseWrapper(Long tenantId, Long applicationId, Long userId, Long profileId) {
        return new LambdaQueryWrapper<MemoryEntity>()
                .eq(MemoryEntity::getTenantId, tenantId)
                .eq(MemoryEntity::getUserId, userId)
                .eq(MemoryEntity::getStatus, STATUS_ACTIVE)
                .and(w -> w.isNull(MemoryEntity::getApplicationId)
                        .or().eq(MemoryEntity::getApplicationId, applicationId))
                .and(w -> w.isNull(MemoryEntity::getProfileId)
                        .or().eq(MemoryEntity::getProfileId, profileId));
    }

    /** Extract meaningful keywords from the user query. */
    private List<String> extractKeywords(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(KEYWORD_SPLITTER.split(query.strip()))
                .map(String::strip)
                .filter(word -> word.length() >= MIN_KEYWORD_LENGTH)
                .distinct()
                .limit(10)
                .collect(Collectors.toList());
    }

    /** Count how many keywords appear in the content. */
    private int countKeywordHits(String content, List<String> keywords) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        String lower = content.toLowerCase();
        int hits = 0;
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase())) {
                hits++;
            }
        }
        return hits;
    }

    private boolean matchesFilter(MemoryEntity memory, MemoryRecallFilter filter, LocalDateTime minUpdatedAt) {
        if (memory == null) {
            return false;
        }
        if (minUpdatedAt != null && memory.getUpdatedAt() != null && memory.getUpdatedAt().isBefore(minUpdatedAt)) {
            return false;
        }
        if (!filter.categories().isEmpty()) {
            String category = normalizeCategory(memory);
            if (!filter.categories().contains(category)) {
                return false;
            }
        }
        if (!filter.requireTags().isEmpty()) {
            Set<String> tags = normalizedTags(memory.getTags());
            if (!tags.containsAll(filter.requireTags())) {
                return false;
            }
        }
        return true;
    }

    private double score(MemoryEntity memory, List<String> keywords) {
        double keywordScore = keywords.isEmpty() ? 0.0 : countKeywordHits(memory.getContent(), keywords);
        double importance = memory.getImportance() == null ? 0.5 : memory.getImportance();
        return keywordScore + Math.max(0.0, Math.min(1.0, importance));
    }

    private double minScore(MemoryRecallFilter filter) {
        return filter.minScore() == null ? 0.0 : filter.minScore();
    }

    private List<MemoryEntity> collapseConflicts(List<MemoryEntity> memories) {
        if (memories == null || memories.isEmpty()) {
            return List.of();
        }
        Set<String> seenConflictKeys = new HashSet<>();
        List<MemoryEntity> result = new ArrayList<>();
        for (MemoryEntity memory : memories) {
            String key = conflictKey(memory);
            if (key == null || seenConflictKeys.add(key)) {
                result.add(memory);
            }
        }
        return result;
    }

    private String conflictKey(MemoryEntity memory) {
        String category = normalizeCategory(memory);
        if (!"preference".equals(category) && !"fact".equals(category)) {
            return null;
        }
        String keyTag = tags(memory.getTags()).stream()
                .map(tag -> tag.strip().toLowerCase())
                .filter(this::isStructuredConflictTag)
                .sorted()
                .findFirst()
                .orElse(null);
        if (keyTag == null) {
            return null;
        }
        return category + ":" + keyTag;
    }

    private boolean isStructuredConflictTag(String tag) {
        return tag.startsWith("pref:")
                || tag.startsWith("key:")
                || tag.endsWith("_style")
                || tag.endsWith("_preference")
                || tag.endsWith("_fact");
    }

    private MemoryDTO toDTO(MemoryEntity memory) {
        return new MemoryDTO(
                memory.getMemoryType(),
                truncateContent(memory.getContent()),
                memory.getMemoryCategory(),
                tags(memory.getTags()),
                memory.getImportance() == null ? 0.5 : memory.getImportance(),
                memory.getSlotHint()
        );
    }

    private void touchReturnedMemories(List<MemoryEntity> memories) {
        if (memories == null || memories.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (MemoryEntity memory : memories) {
            memory.setAccessCount(memory.getAccessCount() == null ? 1 : memory.getAccessCount() + 1);
            memory.setLastAccessedAt(now);
            memoryMapper.updateById(memory);
        }
    }

    private String normalizeCategory(MemoryEntity memory) {
        String category = memory.getMemoryCategory();
        if (category == null || category.isBlank()) {
            category = memory.getMemoryType();
        }
        return category == null ? "" : category.strip().toLowerCase();
    }

    private Set<String> normalizedTags(String[] tags) {
        return tags(tags).stream()
                .map(tag -> tag.strip().toLowerCase())
                .collect(Collectors.toSet());
    }

    private List<String> tags(String[] tags) {
        if (tags == null || tags.length == 0) {
            return List.of();
        }
        return Arrays.stream(tags)
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
    }

    /** Truncate memory content to a reasonable length for context injection. */
    private String truncateContent(String content) {
        if (content == null) {
            return "";
        }
        int maxLen = 300;
        if (content.length() <= maxLen) {
            return content;
        }
        return content.substring(0, maxLen) + "...";
    }
}
