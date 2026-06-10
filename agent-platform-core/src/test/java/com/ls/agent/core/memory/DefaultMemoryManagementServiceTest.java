package com.ls.agent.core.memory;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ls.agent.core.memory.application.DefaultMemoryManagementService;
import com.ls.agent.core.memory.command.UpdateMemoryCommand;
import com.ls.agent.core.memory.dto.MemoryRecordDTO;
import com.ls.agent.core.memory.entity.MemoryEntity;
import com.ls.agent.core.memory.mapper.MemoryMapper;
import com.ls.agent.core.rag.api.EmbeddingService;
import com.ls.agent.core.rag.api.VectorStore;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;
import com.ls.agent.core.rag.dto.VectorStoreDocumentDTO;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultMemoryManagementServiceTest {

    private final MemoryMapper memoryMapper = mock(MemoryMapper.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final DefaultMemoryManagementService service = new DefaultMemoryManagementService(
            memoryMapper,
            embeddingService,
            vectorStore
    );

    @Test
    void listReturnsScopedActiveMemories() {
        MemoryEntity memory = memory(88L, "User likes evening basketball.");
        memory.setTags(new String[]{"sports", "preference"});
        memory.setUpdatedAt(LocalDateTime.parse("2026-06-01T10:00:00"));
        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(memory));

        List<MemoryRecordDTO> result = service.list(
                1L,
                20001L,
                10001L,
                50001L,
                "preference",
                "basketball",
                20
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(88L);
        assertThat(result.get(0).content()).isEqualTo("User likes evening basketball.");
        assertThat(result.get(0).tags()).containsExactly("sports", "preference");
        assertThat(result.get(0).status()).isEqualTo("ACTIVE");
    }

    @Test
    void listUsesTsvectorSearchWhenQueryIsPresent() {
        MemoryEntity memory = memory(88L, "User likes evening basketball.");
        when(memoryMapper.searchActiveMemories(
                eq(1L),
                eq(20001L),
                eq(10001L),
                eq(50001L),
                eq(List.of("basketball", "preference")),
                eq("basketball preference"),
                eq(List.of("PROFILE_LONG_TERM")),
                eq(null),
                eq(20)
        )).thenReturn(List.of(memory));

        List<MemoryRecordDTO> result = service.list(
                1L,
                20001L,
                10001L,
                50001L,
                "preference",
                "basketball preference",
                20
        );

        assertThat(result).extracting(MemoryRecordDTO::id).containsExactly(88L);
        verify(memoryMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void listExcludesExpiredActiveMemoriesFromDefaultManagementView() {
        MemoryEntity expired = memory(87L, "Expired preference");
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));
        MemoryEntity active = memory(88L, "Active preference");
        active.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(expired, active));

        List<MemoryRecordDTO> result = service.list(
                1L,
                20001L,
                10001L,
                50001L,
                "preference",
                null,
                20
        );

        assertThat(result).extracting(MemoryRecordDTO::id).containsExactly(88L);
    }

    @Test
    void listExcludesConversationTemporaryMemoriesFromDefaultManagementView() {
        MemoryEntity temporary = memory(87L, "Current conversation temporary summary");
        temporary.setMemoryType("SUMMARY");
        temporary.setMemoryCategory("summary");
        temporary.setMemoryScope("CONVERSATION_TEMP");
        temporary.setSourceConversationId(90001L);
        MemoryEntity longTerm = memory(88L, "Profile long-term preference");
        longTerm.setMemoryScope("PROFILE_LONG_TERM");
        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(temporary, longTerm));

        List<MemoryRecordDTO> result = service.list(
                1L,
                20001L,
                10001L,
                50001L,
                null,
                null,
                20
        );

        assertThat(result).extracting(MemoryRecordDTO::id).containsExactly(88L);
    }

    @Test
    void updateChangesMemoryAndReindexesMemoryVector() {
        MemoryEntity existing = memory(88L, "Old memory");
        when(memoryMapper.selectById(88L)).thenReturn(existing);
        when(embeddingService.embed("User prefers concise basketball advice."))
                .thenReturn(new EmbeddingVectorDTO("mock", new float[]{1.0f, 0.5f}));

        MemoryRecordDTO result = service.update(new UpdateMemoryCommand(
                1L,
                20001L,
                10001L,
                50001L,
                88L,
                "User prefers concise basketball advice.",
                "preference",
                List.of("sports", "style"),
                0.9,
                "preference",
                null
        ));

        ArgumentCaptor<MemoryEntity> memoryCaptor = ArgumentCaptor.forClass(MemoryEntity.class);
        verify(memoryMapper).updateById(memoryCaptor.capture());
        assertThat(memoryCaptor.getValue().getContent()).isEqualTo("User prefers concise basketball advice.");
        assertThat(memoryCaptor.getValue().getMemoryCategory()).isEqualTo("preference");
        assertThat(memoryCaptor.getValue().getTags()).containsExactly("sports", "style");
        assertThat(memoryCaptor.getValue().getImportance()).isEqualTo(0.9);
        assertThat(memoryCaptor.getValue().getSlotHint()).isEqualTo("preference");
        assertThat(result.content()).isEqualTo("User prefers concise basketball advice.");

        ArgumentCaptor<VectorStoreDocumentDTO> vectorCaptor = ArgumentCaptor.forClass(VectorStoreDocumentDTO.class);
        verify(vectorStore).upsert(vectorCaptor.capture());
        assertThat(vectorCaptor.getValue().sourceType()).isEqualTo("memory");
        assertThat(vectorCaptor.getValue().vectorId()).isEqualTo("memory-88");
        assertThat(vectorCaptor.getValue().documentId()).isEqualTo(88L);
        assertThat(vectorCaptor.getValue().chunkId()).isEqualTo(88L);
    }

    @Test
    void updateCanPinMemoryAndPreservesExistingMetadata() {
        MemoryEntity existing = memory(88L, "User likes evening basketball.");
        existing.setMetadata(JsonNodeFactory.instance.objectNode()
                .put("source_type", "memory")
                .put("vector_index_status", "INDEXED"));
        when(memoryMapper.selectById(88L)).thenReturn(existing);

        MemoryRecordDTO result = service.update(new UpdateMemoryCommand(
                1L,
                20001L,
                10001L,
                50001L,
                88L,
                null,
                null,
                null,
                null,
                null,
                true
        ));

        ArgumentCaptor<MemoryEntity> memoryCaptor = ArgumentCaptor.forClass(MemoryEntity.class);
        verify(memoryMapper).updateById(memoryCaptor.capture());
        assertThat(memoryCaptor.getValue().getMetadata().path("source_type").asText()).isEqualTo("memory");
        assertThat(memoryCaptor.getValue().getMetadata().path("vector_index_status").asText()).isEqualTo("INDEXED");
        assertThat(memoryCaptor.getValue().getMetadata().path("pinned").asBoolean()).isTrue();
        assertThat(result.pinned()).isTrue();
        verify(vectorStore, never()).upsert(any(VectorStoreDocumentDTO.class));
    }

    @Test
    void disableSoftDeletesMemoryAndClearsMemoryVector() {
        MemoryEntity existing = memory(88L, "User likes evening basketball.");
        when(memoryMapper.selectById(88L)).thenReturn(existing);

        int changed = service.disable(1L, 20001L, 10001L, 50001L, 88L);

        assertThat(changed).isEqualTo(1);
        ArgumentCaptor<MemoryEntity> memoryCaptor = ArgumentCaptor.forClass(MemoryEntity.class);
        verify(memoryMapper).updateById(memoryCaptor.capture());
        assertThat(memoryCaptor.getValue().getStatus()).isEqualTo("DISABLED");
        verify(vectorStore).deleteByDocument("memory", 1L, 20001L, 10001L, 50001L, 88L);
    }

    @Test
    void disableAllowsGlobalMemoryVisibleInCurrentApplicationScope() {
        MemoryEntity globalMemory = memory(88L, "User prefers concise replies.");
        globalMemory.setApplicationId(null);
        globalMemory.setProfileId(null);
        when(memoryMapper.selectById(88L)).thenReturn(globalMemory);

        int changed = service.disable(1L, 20001L, 10001L, 50001L, 88L);

        assertThat(changed).isEqualTo(1);
        verify(memoryMapper).updateById(any(MemoryEntity.class));
        verify(vectorStore).deleteByDocument("memory", 1L, null, 10001L, null, 88L);
    }

    @Test
    void disableStillSucceedsWhenVectorCleanupFails() {
        MemoryEntity existing = memory(88L, "User likes evening basketball.");
        when(memoryMapper.selectById(88L)).thenReturn(existing);
        doThrow(new RuntimeException("qdrant down"))
                .when(vectorStore).deleteByDocument("memory", 1L, 20001L, 10001L, 50001L, 88L);

        int changed = service.disable(1L, 20001L, 10001L, 50001L, 88L);

        assertThat(changed).isEqualTo(1);
        verify(memoryMapper).updateById(any(MemoryEntity.class));
    }

    @Test
    void updateIgnoresMemoryOutsideCurrentScope() {
        MemoryEntity otherUserMemory = memory(88L, "Other user memory");
        otherUserMemory.setUserId(99999L);
        when(memoryMapper.selectById(88L)).thenReturn(otherUserMemory);

        MemoryRecordDTO result = service.update(new UpdateMemoryCommand(
                1L,
                20001L,
                10001L,
                50001L,
                88L,
                "Should not update",
                "preference",
                List.of(),
                0.5,
                null,
                null
        ));

        assertThat(result).isNull();
        verify(memoryMapper, never()).updateById(any(MemoryEntity.class));
        verify(vectorStore, never()).upsert(any(VectorStoreDocumentDTO.class));
    }

    @Test
    void updateIgnoresConversationTemporaryMemory() {
        MemoryEntity temporary = memory(88L, "Current conversation temporary summary");
        temporary.setMemoryScope("CONVERSATION_TEMP");
        temporary.setSourceConversationId(90001L);
        when(memoryMapper.selectById(88L)).thenReturn(temporary);

        MemoryRecordDTO result = service.update(new UpdateMemoryCommand(
                1L,
                20001L,
                10001L,
                50001L,
                88L,
                "Should not update",
                "summary",
                List.of(),
                0.5,
                null,
                null
        ));

        assertThat(result).isNull();
        verify(memoryMapper, never()).updateById(any(MemoryEntity.class));
        verify(vectorStore, never()).upsert(any(VectorStoreDocumentDTO.class));
    }

    @Test
    void disableIgnoresConversationTemporaryMemory() {
        MemoryEntity temporary = memory(88L, "Current conversation temporary summary");
        temporary.setMemoryScope("CONVERSATION_TEMP");
        temporary.setSourceConversationId(90001L);
        when(memoryMapper.selectById(88L)).thenReturn(temporary);

        int changed = service.disable(1L, 20001L, 10001L, 50001L, 88L);

        assertThat(changed).isZero();
        verify(memoryMapper, never()).updateById(any(MemoryEntity.class));
        verify(vectorStore, never()).deleteByDocument(any(), any(), any(), any(), any(), any());
    }

    private MemoryEntity memory(Long id, String content) {
        MemoryEntity entity = new MemoryEntity();
        entity.setId(id);
        entity.setTenantId(1L);
        entity.setApplicationId(20001L);
        entity.setUserId(10001L);
        entity.setProfileId(50001L);
        entity.setMemoryType("PREFERENCE");
        entity.setMemoryCategory("preference");
        entity.setContent(content);
        entity.setImportance(0.7);
        entity.setConfidence(0.8);
        entity.setAccessCount(2);
        entity.setStatus("ACTIVE");
        return entity;
    }
}
