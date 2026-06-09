package com.ls.agent.core.memory;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ls.agent.core.memory.application.DefaultMemoryRecallService;
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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class DefaultMemoryRecallServiceTest {

    private final MemoryMapper memoryMapper = mock(MemoryMapper.class);
    private final DefaultMemoryRecallService service = new DefaultMemoryRecallService(memoryMapper);

    @Test
    void recallReturnsActiveMemoriesAsDtos() {
        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(memory("LONG_TERM", "Ada likes concise answers.")));

        List<MemoryDTO> result = service.recall(1L, 20001L, 10001L, 50001L, "Ada", 5);

        assertThat(result).containsExactly(new MemoryDTO("LONG_TERM", "Ada likes concise answers.", null, List.of(), 0.5, null));
    }

    @Test
    void recallUpdatesAccessStatsForReturnedMemories() {
        MemoryEntity entity = memory("PREFERENCE", "Ada likes basketball.");
        entity.setId(10L);
        entity.setAccessCount(2);
        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity));

        service.recall(1L, 20001L, 10001L, 50001L, "basketball", 5);

        ArgumentCaptor<MemoryEntity> captor = ArgumentCaptor.forClass(MemoryEntity.class);
        verify(memoryMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(10L);
        assertThat(captor.getValue().getAccessCount()).isEqualTo(3);
        assertThat(captor.getValue().getLastAccessedAt()).isNotNull();
    }

    @Test
    void recallStillReturnsMemoriesWhenAccessStatsUpdateFails() {
        MemoryEntity entity = memory("PREFERENCE", "Ada likes basketball.");
        entity.setId(10L);
        entity.setAccessCount(2);
        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity));
        doThrow(new IllegalStateException("db update down")).when(memoryMapper).updateById(entity);

        List<MemoryDTO> result = service.recall(1L, 20001L, 10001L, 50001L, "basketball", 5);

        assertThat(result).extracting(MemoryDTO::content).containsExactly("Ada likes basketball.");
    }

    @Test
    void recallWithFilterKeepsMatchingCategoryAndTagsThenRanksByRelevanceImportanceAndRecency() {
        MemoryEntity highImportancePreference = memory("PREFERENCE", "Ada prefers concise basketball advice.");
        highImportancePreference.setMemoryCategory("preference");
        highImportancePreference.setTags(new String[]{"sports", "style"});
        highImportancePreference.setImportance(0.9);
        highImportancePreference.setSlotHint("task_memory");

        MemoryEntity lowerImportancePreference = memory("PREFERENCE", "Ada likes basketball after work.");
        lowerImportancePreference.setMemoryCategory("preference");
        lowerImportancePreference.setTags(new String[]{"sports"});
        lowerImportancePreference.setImportance(0.4);

        MemoryEntity wrongCategory = memory("FACT", "Ada works in Chongqing and likes basketball.");
        wrongCategory.setMemoryCategory("fact");
        wrongCategory.setTags(new String[]{"sports"});
        wrongCategory.setImportance(1.0);

        MemoryEntity missingTag = memory("PREFERENCE", "Ada prefers concise answers.");
        missingTag.setMemoryCategory("preference");
        missingTag.setTags(new String[]{"writing"});
        missingTag.setImportance(1.0);

        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                lowerImportancePreference,
                wrongCategory,
                highImportancePreference,
                missingTag
        ));

        List<MemoryDTO> result = service.recall(
                1L,
                20001L,
                10001L,
                50001L,
                "basketball concise",
                MemoryRecallFilter.builder()
                        .categories(List.of("preference"))
                        .requireTags(List.of("sports"))
                        .topK(5)
                        .build()
        );

        assertThat(result).extracting(MemoryDTO::content).containsExactly(
                "Ada prefers concise basketball advice.",
                "Ada likes basketball after work."
        );
        assertThat(result.get(0).memoryCategory()).isEqualTo("preference");
        assertThat(result.get(0).tags()).containsExactly("sports", "style");
        assertThat(result.get(0).importance()).isEqualTo(0.9);
        assertThat(result.get(0).slotHint()).isEqualTo("task_memory");
    }

    @Test
    void recallExcludesExpiredMemoriesFromContextInjection() {
        MemoryEntity expired = memory("PREFERENCE", "Ada likes expired basketball tips.");
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));
        expired.setImportance(1.0);

        MemoryEntity active = memory("PREFERENCE", "Ada likes active basketball tips.");
        active.setExpiresAt(LocalDateTime.now().plusDays(1));
        active.setImportance(0.8);

        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(expired, active));

        List<MemoryDTO> result = service.recall(1L, 20001L, 10001L, 50001L, "basketball", 5);

        assertThat(result).extracting(MemoryDTO::content)
                .containsExactly("Ada likes active basketball tips.");
    }

    @Test
    void recallUsesVectorResultsBeforeKeywordFallbackWhenSemanticSearchFindsMemories() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        VectorStore vectorStore = mock(VectorStore.class);
        DefaultMemoryRecallService vectorService = new DefaultMemoryRecallService(memoryMapper, embeddingService, vectorStore);
        MemoryEntity semanticMemory = memory("PREFERENCE", "User likes evening basketball.");
        semanticMemory.setId(88L);
        semanticMemory.setTenantId(1L);
        semanticMemory.setUserId(10001L);
        semanticMemory.setMemoryCategory("preference");
        semanticMemory.setApplicationId(20001L);
        semanticMemory.setProfileId(50001L);
        when(embeddingService.embed("sports after work"))
                .thenReturn(new EmbeddingVectorDTO("mock", new float[]{1.0f, 0.0f}));
        when(vectorStore.search(any(VectorSearchQueryDTO.class)))
                .thenReturn(List.of(new VectorSearchResultDTO("memory-88", 88L, 88L, 0.91)));
        when(memoryMapper.selectBatchIds(any(java.util.Collection.class))).thenReturn(List.of(semanticMemory));
        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        List<MemoryDTO> result = vectorService.recall(
                1L,
                20001L,
                10001L,
                50001L,
                "sports after work",
                MemoryRecallFilter.builder()
                        .categories(List.of("preference"))
                        .topK(5)
                        .build()
        );

        assertThat(result).extracting(MemoryDTO::content).containsExactly("User likes evening basketball.");
        ArgumentCaptor<VectorSearchQueryDTO> queryCaptor = ArgumentCaptor.forClass(VectorSearchQueryDTO.class);
        verify(vectorStore).search(queryCaptor.capture());
        assertThat(queryCaptor.getValue().sourceType()).isEqualTo("memory");
        assertThat(queryCaptor.getValue().tenantId()).isEqualTo(1L);
        assertThat(queryCaptor.getValue().applicationId()).isEqualTo(20001L);
        assertThat(queryCaptor.getValue().ownerUserId()).isEqualTo(10001L);
        assertThat(queryCaptor.getValue().profileId()).isEqualTo(50001L);
    }

    @Test
    void recallMergesVectorAndKeywordCandidatesWhenBothPathsFindMemories() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        VectorStore vectorStore = mock(VectorStore.class);
        DefaultMemoryRecallService vectorService = new DefaultMemoryRecallService(memoryMapper, embeddingService, vectorStore);
        MemoryEntity semanticMemory = memory("PREFERENCE", "User likes evening basketball.");
        semanticMemory.setId(88L);
        semanticMemory.setTenantId(1L);
        semanticMemory.setUserId(10001L);
        semanticMemory.setApplicationId(20001L);
        semanticMemory.setProfileId(50001L);
        semanticMemory.setMemoryCategory("preference");
        semanticMemory.setImportance(0.7);
        MemoryEntity keywordMemory = memory("PREFERENCE", "User prefers basketball advice with injury prevention tips.");
        keywordMemory.setId(99L);
        keywordMemory.setMemoryCategory("preference");
        keywordMemory.setImportance(0.8);
        when(embeddingService.embed("basketball injury prevention"))
                .thenReturn(new EmbeddingVectorDTO("mock", new float[]{1.0f, 0.0f}));
        when(vectorStore.search(any(VectorSearchQueryDTO.class)))
                .thenReturn(List.of(new VectorSearchResultDTO("memory-88", 88L, 88L, 0.91)));
        when(memoryMapper.selectBatchIds(any(java.util.Collection.class))).thenReturn(List.of(semanticMemory));
        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(keywordMemory));

        List<MemoryDTO> result = vectorService.recall(
                1L,
                20001L,
                10001L,
                50001L,
                "basketball injury prevention",
                MemoryRecallFilter.builder()
                        .categories(List.of("preference"))
                        .topK(5)
                        .build()
        );

        assertThat(result).extracting(MemoryDTO::content).containsExactlyInAnyOrder(
                "User likes evening basketball.",
                "User prefers basketball advice with injury prevention tips."
        );
    }

    @Test
    void recallRecordsMemoryEmbeddingAndVectorSearchSpansWhenTraceContextExists() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        VectorStore vectorStore = mock(VectorStore.class);
        TraceService traceService = mock(TraceService.class);
        DefaultMemoryRecallService vectorService = new DefaultMemoryRecallService(memoryMapper, embeddingService, vectorStore, traceService);
        when(traceService.startSpan(any(StartTraceSpanCommand.class)))
                .thenAnswer(invocation -> {
                    StartTraceSpanCommand command = invocation.getArgument(0);
                    long id = switch (command.spanName()) {
                        case "memory.recall" -> 70000L;
                        case "memory.embedding" -> 70001L;
                        case "memory.vector.search" -> 70002L;
                        case "memory.keyword.search" -> 70003L;
                        default -> 70999L;
                    };
                    return new TraceSpanDTO(id, command.traceId(), command.parentSpanId(), command.spanName(),
                            command.spanType(), command.component(), "RUNNING", LocalDateTime.now(), null,
                            null, null, null, command.attributes(), LocalDateTime.now());
                });
        MemoryEntity semanticMemory = memory("PREFERENCE", "User likes evening basketball.");
        semanticMemory.setId(88L);
        semanticMemory.setTenantId(1L);
        semanticMemory.setUserId(10001L);
        semanticMemory.setApplicationId(20001L);
        semanticMemory.setProfileId(50001L);
        semanticMemory.setMemoryCategory("preference");
        when(embeddingService.embed("sports after work"))
                .thenReturn(new EmbeddingVectorDTO("mock", new float[]{1.0f, 0.0f}));
        when(vectorStore.search(any(VectorSearchQueryDTO.class)))
                .thenReturn(List.of(new VectorSearchResultDTO("memory-88", 88L, 88L, 0.91)));
        when(memoryMapper.selectBatchIds(any(java.util.Collection.class))).thenReturn(List.of(semanticMemory));

        List<MemoryDTO> result = vectorService.recall(
                1L,
                20001L,
                10001L,
                50001L,
                "sports after work",
                MemoryRecallFilter.builder().categories(List.of("preference")).topK(5).build(),
                "trace-1",
                42L
        );

        assertThat(result).hasSize(1);
        ArgumentCaptor<StartTraceSpanCommand> startCaptor = ArgumentCaptor.forClass(StartTraceSpanCommand.class);
        verify(traceService, org.mockito.Mockito.times(4)).startSpan(startCaptor.capture());
        assertThat(startCaptor.getAllValues()).extracting(StartTraceSpanCommand::spanName)
                .containsExactly("memory.recall", "memory.embedding", "memory.vector.search", "memory.keyword.search");
        assertThat(startCaptor.getAllValues()).allSatisfy(command -> assertThat(command.traceId()).isEqualTo("trace-1"));
        assertThat(startCaptor.getAllValues().get(0).parentSpanId()).isEqualTo(42L);
        assertThat(startCaptor.getAllValues().subList(1, 4))
                .allSatisfy(command -> assertThat(command.parentSpanId()).isEqualTo(70000L));
        assertThat(startCaptor.getAllValues()).allSatisfy(command -> assertThat(command.component()).isEqualTo("core.memory"));
        assertThat(startCaptor.getAllValues().get(0).attributes().path("topK").asInt()).isEqualTo(5);
        assertThat(startCaptor.getAllValues().get(0).attributes().path("queryChars").isMissingNode()).isTrue();
        assertThat(startCaptor.getAllValues().get(3).attributes().path("keywordCount").asInt()).isEqualTo(3);
        ArgumentCaptor<FinishTraceSpanCommand> finishCaptor = ArgumentCaptor.forClass(FinishTraceSpanCommand.class);
        verify(traceService, org.mockito.Mockito.times(4)).finishSpan(finishCaptor.capture());
        assertThat(finishCaptor.getAllValues()).extracting(FinishTraceSpanCommand::status)
                .containsExactly("SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS");
        assertThat(finishCaptor.getAllValues().get(0).attributes().path("dimension").asInt()).isEqualTo(2);
        assertThat(finishCaptor.getAllValues().get(1).attributes().path("resultCount").asInt()).isEqualTo(1);
        assertThat(finishCaptor.getAllValues().get(2).attributes().path("candidateCount").asInt()).isZero();
        assertThat(finishCaptor.getAllValues().get(3).attributes().path("returnedCount").asInt()).isEqualTo(1);
    }

    @Test
    void recallCollapsesConflictingPreferencesByCategoryAndTag() {
        MemoryEntity oldPreference = memory("PREFERENCE", "User prefers long detailed answers.");
        oldPreference.setId(10L);
        oldPreference.setMemoryCategory("preference");
        oldPreference.setTags(new String[]{"answer_style"});
        oldPreference.setImportance(0.5);
        oldPreference.setUpdatedAt(LocalDateTime.of(2026, 6, 1, 10, 0));

        MemoryEntity newPreference = memory("PREFERENCE", "User prefers concise answers.");
        newPreference.setId(11L);
        newPreference.setMemoryCategory("preference");
        newPreference.setTags(new String[]{"answer_style"});
        newPreference.setImportance(0.9);
        newPreference.setUpdatedAt(LocalDateTime.of(2026, 6, 8, 10, 0));

        MemoryEntity otherPreference = memory("PREFERENCE", "User prefers Java examples.");
        otherPreference.setId(12L);
        otherPreference.setMemoryCategory("preference");
        otherPreference.setTags(new String[]{"language"});
        otherPreference.setImportance(0.7);
        otherPreference.setUpdatedAt(LocalDateTime.of(2026, 6, 7, 10, 0));

        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(oldPreference, newPreference, otherPreference));

        List<MemoryDTO> result = service.recall(
                1L,
                20001L,
                10001L,
                50001L,
                "answer style Java",
                MemoryRecallFilter.builder()
                        .categories(List.of("preference"))
                        .topK(5)
                        .build()
        );

        assertThat(result).extracting(MemoryDTO::content).containsExactly(
                "User prefers concise answers.",
                "User prefers Java examples."
        );
    }

    @Test
    void recallKeepsPinnedMemoryWhenConflictingPreferenceWouldOtherwiseRankHigher() {
        MemoryEntity pinnedPreference = memory("PREFERENCE", "User prefers long detailed answers.");
        pinnedPreference.setId(10L);
        pinnedPreference.setMemoryCategory("preference");
        pinnedPreference.setTags(new String[]{"answer_style"});
        pinnedPreference.setImportance(0.2);
        pinnedPreference.setUpdatedAt(LocalDateTime.of(2026, 6, 1, 10, 0));
        pinnedPreference.setMetadata(JsonNodeFactory.instance.objectNode().put("pinned", true));

        MemoryEntity highRankPreference = memory("PREFERENCE", "User prefers concise answers.");
        highRankPreference.setId(11L);
        highRankPreference.setMemoryCategory("preference");
        highRankPreference.setTags(new String[]{"answer_style"});
        highRankPreference.setImportance(1.0);
        highRankPreference.setUpdatedAt(LocalDateTime.of(2026, 6, 8, 10, 0));

        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(highRankPreference, pinnedPreference));

        List<MemoryDTO> result = service.recall(
                1L,
                20001L,
                10001L,
                50001L,
                "answer style",
                MemoryRecallFilter.builder()
                        .categories(List.of("preference"))
                        .topK(5)
                        .build()
        );

        assertThat(result).extracting(MemoryDTO::content).containsExactly("User prefers long detailed answers.");
    }

    private MemoryEntity memory(String type, String content) {
        MemoryEntity entity = new MemoryEntity();
        entity.setMemoryType(type);
        entity.setContent(content);
        entity.setStatus("ACTIVE");
        return entity;
    }
}
