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
    private static final String SCOPE_PROFILE_LONG_TERM = "PROFILE_LONG_TERM";
    private static final String SCOPE_CONVERSATION_TEMP = "CONVERSATION_TEMP";
    private static final int FETCH_MULTIPLIER = 10;
    private static final double RRF_K = 60.0;
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
        return recall(tenantId, applicationId, userId, profileId, query, filter, null, traceId, parentSpanId);
    }

    @Override
    public List<MemoryDTO> recall(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            MemoryRecallFilter filter,
            EmbeddingVectorDTO queryVector,
            String traceId,
            Long parentSpanId
    ) {
        MemoryRecallFilter resolvedFilter = filter == null
                ? MemoryRecallFilter.builder().build()
                : filter;
        int limit = resolvedFilter.resolvedTopK(5);
        List<String> keywords = extractKeywords(query);
        TraceSpanDTO recallSpan = safeStartSpan(traceId, parentSpanId, "memory.recall",
                attributes()
                        .put("topK", limit)
                        .put("categoryCount", resolvedFilter.categories().size())
                        .put("requiredTagCount", resolvedFilter.requireTags().size())
                        .put("keywordCount", keywords.size())
                        .put("profileScoped", profileId != null));
        try {
            Long childParentSpanId = recallSpan == null ? parentSpanId : recallSpan.id();
            List<MemoryEntity> vectorMemories = recallByVector(
                    tenantId,
                    applicationId,
                    userId,
                    profileId,
                    query,
                    resolvedFilter,
                    limit,
                    queryVector,
                    traceId,
                    childParentSpanId
            );
            List<MemoryEntity> keywordMemories = recallByKeyword(
                    tenantId,
                    applicationId,
                    userId,
                    profileId,
                    resolvedFilter,
                    limit,
                    keywords,
                    traceId,
                    childParentSpanId
            );
            List<MemoryEntity> returned = mergeRecallCandidates(vectorMemories, keywordMemories, keywords, limit);
            if (recallSpan != null && recallSpan.attributes() instanceof com.fasterxml.jackson.databind.node.ObjectNode attributes) {
                attributes.put("vectorCandidateCount", vectorMemories == null ? 0 : vectorMemories.size());
                attributes.put("keywordCandidateCount", keywordMemories == null ? 0 : keywordMemories.size());
                attributes.put("returnedCount", returned.size());
            }
            touchReturnedMemories(returned);
            List<MemoryDTO> result = returned.stream()
                    .map(this::toDTO)
                    .toList();
            safeFinishSpan(recallSpan, "SUCCESS", null, null);
            return result;
        } catch (RuntimeException ex) {
            safeFinishSpan(recallSpan, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    private List<MemoryEntity> recallByKeyword(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            MemoryRecallFilter resolvedFilter,
            int limit,
            List<String> keywords,
            String traceId,
            Long parentSpanId
    ) {
        TraceSpanDTO keywordSpan = safeStartSpan(traceId, parentSpanId, "memory.keyword.search",
                attributes()
                        .put("topK", Math.max(1, limit * FETCH_MULTIPLIER))
                        .put("keywordCount", keywords == null ? 0 : keywords.size()));
        LambdaQueryWrapper<MemoryEntity> wrapper = baseWrapper(tenantId, applicationId, userId, profileId);

        if (!keywords.isEmpty()) {
            List<MemoryEntity> tsvectorCandidates = searchByTsvector(
                    tenantId,
                    applicationId,
                    userId,
                    profileId,
                    resolvedFilter,
                    keywords,
                    Math.max(1, limit * FETCH_MULTIPLIER)
            );
            if (!tsvectorCandidates.isEmpty()) {
                return filterAndRankKeywordCandidates(tsvectorCandidates, resolvedFilter, keywords, keywordSpan);
            }
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

        try {
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
            if (keywordSpan != null && keywordSpan.attributes() instanceof com.fasterxml.jackson.databind.node.ObjectNode attributes) {
                attributes.put("candidateCount", candidates.size());
                attributes.put("resultCount", scored.size());
            }
            safeFinishSpan(keywordSpan, "SUCCESS", null, null);
            return scored;
        } catch (RuntimeException ex) {
            safeFinishSpan(keywordSpan, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    private List<MemoryEntity> searchByTsvector(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            MemoryRecallFilter filter,
            List<String> keywords,
            int limit
    ) {
        try {
            List<MemoryEntity> memories = memoryMapper.searchActiveMemories(
                    tenantId,
                    applicationId,
                    userId,
                    profileId,
                    keywords,
                    String.join(" ", keywords),
                    filter.memoryScopes(),
                    filter.sourceConversationId(),
                    limit
            );
            return memories == null ? List.of() : memories;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<MemoryEntity> filterAndRankKeywordCandidates(
            List<MemoryEntity> candidates,
            MemoryRecallFilter resolvedFilter,
            List<String> keywords,
            TraceSpanDTO keywordSpan
    ) {
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
        if (keywordSpan != null && keywordSpan.attributes() instanceof com.fasterxml.jackson.databind.node.ObjectNode attributes) {
            attributes.put("candidateCount", candidates.size());
            attributes.put("resultCount", scored.size());
            attributes.put("keywordSearchMode", "tsvector");
        }
        safeFinishSpan(keywordSpan, "SUCCESS", null, null);
        return scored;
    }

    private List<MemoryEntity> mergeRecallCandidates(
            List<MemoryEntity> vectorMemories,
            List<MemoryEntity> keywordMemories,
            List<String> keywords,
            int limit
    ) {
        List<MemoryEntity> safeVectorMemories = vectorMemories == null ? List.of() : vectorMemories;
        List<MemoryEntity> safeKeywordMemories = keywordMemories == null ? List.of() : keywordMemories;
        if (safeVectorMemories.isEmpty()) {
            return collapseConflicts(safeKeywordMemories).stream()
                    .limit(Math.max(1, limit))
                    .toList();
        }
        if (safeKeywordMemories.isEmpty()) {
            return safeVectorMemories.stream()
                    .limit(Math.max(1, limit))
                    .toList();
        }
        Map<String, FusedMemory> fused = new LinkedHashMap<>();
        addRankedMemories(fused, safeVectorMemories);
        addRankedMemories(fused, safeKeywordMemories);
        return collapseConflicts(fused.values().stream()
                .sorted(Comparator
                        .<FusedMemory>comparingDouble(FusedMemory::score)
                        .reversed()
                        .thenComparing(fusedMemory -> score(fusedMemory.memory(), keywords), Comparator.reverseOrder())
                        .thenComparing(fusedMemory -> fusedMemory.memory().getUpdatedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .map(FusedMemory::memory)
                .toList())
                .stream()
                .limit(Math.max(1, limit))
                .toList();
    }

    private void addRankedMemories(Map<String, FusedMemory> fused, List<MemoryEntity> memories) {
        for (int i = 0; i < memories.size(); i++) {
            MemoryEntity memory = memories.get(i);
            if (memory == null) {
                continue;
            }
            String key = memoryKey(memory);
            double score = 1.0 / (RRF_K + i + 1);
            fused.compute(key, (ignored, existing) -> {
                if (existing == null) {
                    return new FusedMemory(memory, score);
                }
                return new FusedMemory(existing.memory(), existing.score() + score);
            });
        }
    }

    private String memoryKey(MemoryEntity memory) {
        if (memory.getId() != null) {
            return "id:" + memory.getId();
        }
        return "content:" + (memory.getContent() == null ? "" : memory.getContent());
    }

    private List<MemoryEntity> recallByVector(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            MemoryRecallFilter filter,
            int limit,
            EmbeddingVectorDTO precomputedQueryVector,
            String traceId,
            Long parentSpanId
    ) {
        if (vectorStore == null || query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        try {
            EmbeddingVectorDTO queryVector = precomputedQueryVector == null
                    ? embedQuery(query, traceId, parentSpanId)
                    : precomputedQueryVector;
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
                        Math.max(1, limit * FETCH_MULTIPLIER),
                        filter.memoryScopes(),
                        filter.sourceConversationId()
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

    private EmbeddingVectorDTO embedQuery(String query, String traceId, Long parentSpanId) {
        if (embeddingService == null) {
            return null;
        }
        TraceSpanDTO embeddingSpan = safeStartSpan(traceId, parentSpanId, "memory.embedding", attributes().put("queryChars", query.length()));
        try {
            EmbeddingVectorDTO queryVector = embeddingService.embed(query);
            if (embeddingSpan != null && embeddingSpan.attributes() instanceof com.fasterxml.jackson.databind.node.ObjectNode attributes) {
                attributes.put("model", queryVector == null ? "" : queryVector.model());
                attributes.put("dimension", queryVector == null ? 0 : queryVector.dimension());
            }
            safeFinishSpan(embeddingSpan, "SUCCESS", null, null);
            return queryVector;
        } catch (RuntimeException ex) {
            safeFinishSpan(embeddingSpan, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
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
        return applicationMatches && profileMatches && STATUS_ACTIVE.equals(memory.getStatus()) && !isExpired(memory);
    }

    private LambdaQueryWrapper<MemoryEntity> baseWrapper(Long tenantId, Long applicationId, Long userId, Long profileId) {
        return new LambdaQueryWrapper<MemoryEntity>()
                .eq(MemoryEntity::getTenantId, tenantId)
                .eq(MemoryEntity::getUserId, userId)
                .eq(MemoryEntity::getStatus, STATUS_ACTIVE)
                .and(w -> w.isNull(MemoryEntity::getExpiresAt)
                        .or().gt(MemoryEntity::getExpiresAt, LocalDateTime.now()))
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
        if (isExpired(memory)) {
            return false;
        }
        if (minUpdatedAt != null && memory.getUpdatedAt() != null && memory.getUpdatedAt().isBefore(minUpdatedAt)) {
            return false;
        }
        if (!matchesMemoryScope(memory, filter)) {
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

    private boolean matchesMemoryScope(MemoryEntity memory, MemoryRecallFilter filter) {
        String memoryScope = normalizeMemoryScope(memory);
        if (filter.memoryScopes().isEmpty()) {
            return !SCOPE_CONVERSATION_TEMP.equals(memoryScope);
        }
        if (!filter.memoryScopes().contains(memoryScope)) {
            return false;
        }
        if (SCOPE_CONVERSATION_TEMP.equals(memoryScope)) {
            return filter.sourceConversationId() != null
                    && Objects.equals(memory.getSourceConversationId(), filter.sourceConversationId());
        }
        return true;
    }

    private String normalizeMemoryScope(MemoryEntity memory) {
        String scope = memory.getMemoryScope();
        if (scope == null || scope.isBlank()) {
            return SCOPE_PROFILE_LONG_TERM;
        }
        return scope.strip().toUpperCase(Locale.ROOT);
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
        Map<String, Integer> conflictIndexes = new HashMap<>();
        List<MemoryEntity> result = new ArrayList<>();
        for (MemoryEntity memory : memories) {
            String key = conflictKey(memory);
            if (key == null) {
                result.add(memory);
                continue;
            }
            Integer existingIndex = conflictIndexes.get(key);
            if (existingIndex == null) {
                conflictIndexes.put(key, result.size());
                result.add(memory);
                continue;
            }
            MemoryEntity existing = result.get(existingIndex);
            if (isPinned(memory) && !isPinned(existing)) {
                result.set(existingIndex, memory);
            }
        }
        return result;
    }

    private boolean isPinned(MemoryEntity memory) {
        return memory != null && memory.getMetadata() != null && memory.getMetadata().path("pinned").asBoolean(false);
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
            try {
                memory.setAccessCount(memory.getAccessCount() == null ? 1 : memory.getAccessCount() + 1);
                memory.setLastAccessedAt(now);
                memoryMapper.updateById(memory);
            } catch (RuntimeException ignored) {
                // Access stats are telemetry; recall results must remain available if this write fails.
            }
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

    private boolean isExpired(MemoryEntity memory) {
        return memory.getExpiresAt() != null && !memory.getExpiresAt().isAfter(LocalDateTime.now());
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

    private record FusedMemory(
            MemoryEntity memory,
            double score
    ) {
    }
}
