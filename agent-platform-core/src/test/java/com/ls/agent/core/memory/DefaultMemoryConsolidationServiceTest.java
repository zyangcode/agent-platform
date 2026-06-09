package com.ls.agent.core.memory;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ls.agent.core.memory.application.DefaultMemoryConsolidationService;
import com.ls.agent.core.memory.dto.MemoryConsolidationResult;
import com.ls.agent.core.memory.entity.MemoryEntity;
import com.ls.agent.core.memory.mapper.MemoryMapper;
import com.ls.agent.core.rag.api.VectorStore;
import com.ls.agent.core.trace.api.TraceService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultMemoryConsolidationServiceTest {

    private final MemoryMapper memoryMapper = mock(MemoryMapper.class);
    private final TraceService traceService = mock(TraceService.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final DefaultMemoryConsolidationService service = new DefaultMemoryConsolidationService(memoryMapper, traceService, vectorStore);

    @Test
    void consolidateArchivesExpiredMemoriesAndDecaysStaleImportance() {
        MemoryEntity expired = memory(1L, "old expired preference", 0.9);
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));

        MemoryEntity stale = memory(2L, "stale weak summary", 0.6);
        stale.setLastAccessedAt(LocalDateTime.now().minusDays(40));

        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(expired, stale));

        MemoryConsolidationResult result = service.consolidate(1L, 10001L, 20001L, 50001L);

        ArgumentCaptor<MemoryEntity> captor = ArgumentCaptor.forClass(MemoryEntity.class);
        verify(memoryMapper, org.mockito.Mockito.times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues().get(0).getId()).isEqualTo(1L);
        assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo("ARCHIVED");
        assertThat(captor.getAllValues().get(1).getId()).isEqualTo(2L);
        assertThat(captor.getAllValues().get(1).getImportance()).isLessThan(0.6);
        verify(memoryMapper, never()).deleteById(1L);
        verify(vectorStore).deleteByDocument("memory", 1L, 20001L, 10001L, 50001L, 1L);
        assertThat(result.expiredCount()).isEqualTo(1);
        assertThat(result.decayedCount()).isEqualTo(1);
    }

    @Test
    void consolidateMergesDuplicateMemories() {
        MemoryEntity first = memory(1L, "User likes basketball after work", 0.4);
        first.setTags(new String[]{"sports"});
        MemoryEntity duplicate = memory(2L, "User likes basketball after work", 0.9);
        duplicate.setTags(new String[]{"preference"});
        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, duplicate));

        MemoryConsolidationResult result = service.consolidate(1L, 10001L, 20001L, 50001L);

        ArgumentCaptor<MemoryEntity> captor = ArgumentCaptor.forClass(MemoryEntity.class);
        verify(memoryMapper).updateById(captor.capture());
        verify(memoryMapper).deleteById(2L);
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getImportance()).isEqualTo(0.9);
        assertThat(captor.getValue().getTags()).containsExactly("sports", "preference");
        assertThat(result.mergedCount()).isEqualTo(1);
    }

    @Test
    void consolidateDoesNotDecayPinnedStaleMemory() {
        MemoryEntity pinned = memory(1L, "Pinned answer style", 0.6);
        pinned.setLastAccessedAt(LocalDateTime.now().minusDays(60));
        pinned.setMetadata(JsonNodeFactory.instance.objectNode().put("pinned", true));
        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(pinned));

        MemoryConsolidationResult result = service.consolidate(1L, 10001L, 20001L, 50001L);

        verify(memoryMapper, never()).updateById(pinned);
        assertThat(pinned.getImportance()).isEqualTo(0.6);
        assertThat(result.decayedCount()).isZero();
    }

    @Test
    void consolidateDoesNotMergePinnedDuplicateMemory() {
        MemoryEntity pinned = memory(1L, "User likes basketball after work", 0.4);
        pinned.setMetadata(JsonNodeFactory.instance.objectNode().put("pinned", true));
        MemoryEntity duplicate = memory(2L, "User likes basketball after work", 0.9);
        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(pinned, duplicate));

        MemoryConsolidationResult result = service.consolidate(1L, 10001L, 20001L, 50001L);

        verify(memoryMapper, never()).deleteById(2L);
        verify(vectorStore, never()).deleteByDocument("memory", 1L, 20001L, 10001L, 50001L, 2L);
        assertThat(result.mergedCount()).isZero();
    }

    @Test
    void consolidateDeletesMemoryVectorWhenDuplicateMemoryIsRemoved() {
        MemoryEntity first = memory(1L, "User likes basketball after work", 0.4);
        MemoryEntity duplicate = memory(2L, "User likes basketball after work", 0.9);
        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, duplicate));

        service.consolidate(1L, 10001L, 20001L, 50001L);

        verify(vectorStore).deleteByDocument("memory", 1L, 20001L, 10001L, 50001L, 2L);
    }

    @Test
    void consolidateRecordsTraceSpanWhenTraceIdIsProvided() {
        MemoryEntity first = memory(1L, "User likes basketball after work", 0.4);
        MemoryEntity duplicate = memory(2L, "User likes basketball after work", 0.9);
        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, duplicate));
        when(traceService.startSpan(any(StartTraceSpanCommand.class)))
                .thenAnswer(invocation -> {
                    StartTraceSpanCommand command = invocation.getArgument(0);
                    return new TraceSpanDTO(77L, command.traceId(), command.parentSpanId(), command.spanName(),
                            command.spanType(), command.component(), "RUNNING", null, null,
                            null, null, null, command.attributes(), null);
                });

        service.consolidate(1L, 10001L, 20001L, 50001L, "trace-1", 42L);

        ArgumentCaptor<StartTraceSpanCommand> captor = ArgumentCaptor.forClass(StartTraceSpanCommand.class);
        verify(traceService).startSpan(captor.capture());
        assertThat(captor.getValue().traceId()).isEqualTo("trace-1");
        assertThat(captor.getValue().parentSpanId()).isEqualTo(42L);
        assertThat(captor.getValue().spanName()).isEqualTo("memory.consolidate");
        assertThat(captor.getValue().spanType()).isEqualTo("MEMORY");
        assertThat(captor.getValue().attributes().get("scannedCount").asInt()).isEqualTo(2);
        assertThat(captor.getValue().attributes().get("mergedCount").asInt()).isEqualTo(1);
    }

    private MemoryEntity memory(Long id, String content, double importance) {
        MemoryEntity entity = new MemoryEntity();
        entity.setId(id);
        entity.setTenantId(1L);
        entity.setUserId(10001L);
        entity.setApplicationId(20001L);
        entity.setProfileId(50001L);
        entity.setMemoryType("PREFERENCE");
        entity.setMemoryCategory("preference");
        entity.setContent(content);
        entity.setImportance(importance);
        entity.setStatus("ACTIVE");
        return entity;
    }
}
