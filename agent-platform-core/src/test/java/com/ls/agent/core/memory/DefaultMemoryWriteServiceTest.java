package com.ls.agent.core.memory;

import com.ls.agent.core.memory.application.DefaultMemoryWriteService;
import com.ls.agent.core.memory.command.RecordMemoryCommand;
import com.ls.agent.core.memory.entity.MemoryEntity;
import com.ls.agent.core.memory.mapper.MemoryMapper;
import com.ls.agent.core.rag.api.EmbeddingService;
import com.ls.agent.core.rag.api.VectorStore;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;
import com.ls.agent.core.rag.dto.VectorStoreDocumentDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultMemoryWriteServiceTest {

    private final MemoryMapper memoryMapper = mock(MemoryMapper.class);
    private final DefaultMemoryWriteService service = new DefaultMemoryWriteService(memoryMapper);

    @Test
    void recordStoresActiveMemory() {
        service.record(new RecordMemoryCommand(
                1L,
                10001L,
                20001L,
                50001L,
                "SUMMARY",
                "User: hello\nAssistant: done",
                90001L,
                "summary",
                List.of("chat", "result"),
                0.7,
                "task_memory"
        ));

        ArgumentCaptor<MemoryEntity> captor = ArgumentCaptor.forClass(MemoryEntity.class);
        verify(memoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(1L);
        assertThat(captor.getValue().getMemoryType()).isEqualTo("SUMMARY");
        assertThat(captor.getValue().getMemoryCategory()).isEqualTo("summary");
        assertThat(captor.getValue().getTags()).containsExactly("chat", "result");
        assertThat(captor.getValue().getImportance()).isEqualTo(0.7);
        assertThat(captor.getValue().getConfidence()).isEqualTo(0.8);
        assertThat(captor.getValue().getAccessCount()).isZero();
        assertThat(captor.getValue().getLastAccessedAt()).isNotNull();
        assertThat(captor.getValue().getSlotHint()).isEqualTo("task_memory");
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().getSourceConversationId()).isEqualTo(90001L);
    }

    @Test
    void recordUpdatesExistingSimilarMemoryAccessStats() {
        MemoryEntity existing = new MemoryEntity();
        existing.setId(10L);
        existing.setTenantId(1L);
        existing.setUserId(10001L);
        existing.setApplicationId(20001L);
        existing.setProfileId(50001L);
        existing.setMemoryType("PREFERENCE");
        existing.setMemoryCategory("preference");
        existing.setContent("User likes basketball");
        existing.setImportance(0.4);
        existing.setAccessCount(2);

        when(memoryMapper.selectList(any())).thenReturn(List.of(existing));

        service.record(new RecordMemoryCommand(
                1L,
                10001L,
                20001L,
                50001L,
                "PREFERENCE",
                "User likes basketball after work",
                90001L,
                "preference",
                List.of("sports"),
                0.9,
                "preference"
        ));

        ArgumentCaptor<MemoryEntity> captor = ArgumentCaptor.forClass(MemoryEntity.class);
        verify(memoryMapper).updateById(captor.capture());
        assertThat(captor.getValue().getAccessCount()).isEqualTo(3);
        assertThat(captor.getValue().getLastAccessedAt()).isNotNull();
        assertThat(captor.getValue().getImportance()).isEqualTo(0.9);
        assertThat(captor.getValue().getTags()).containsExactly("sports");
    }

    @Test
    void recordIndexesInsertedMemoryIntoVectorStoreWhenEmbeddingIsAvailable() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        VectorStore vectorStore = mock(VectorStore.class);
        DefaultMemoryWriteService vectorService = new DefaultMemoryWriteService(memoryMapper, embeddingService, vectorStore);
        when(memoryMapper.selectList(any())).thenReturn(List.of());
        when(embeddingService.embed("User likes basketball after work"))
                .thenReturn(new EmbeddingVectorDTO("mock", new float[]{1.0f, 0.5f}));
        org.mockito.Mockito.doAnswer(invocation -> {
            MemoryEntity entity = invocation.getArgument(0);
            entity.setId(88L);
            return 1;
        }).when(memoryMapper).insert(any(MemoryEntity.class));

        vectorService.record(new RecordMemoryCommand(
                1L,
                10001L,
                20001L,
                50001L,
                "PREFERENCE",
                "User likes basketball after work",
                90001L,
                "preference",
                List.of("sports"),
                0.9,
                "preference"
        ));

        ArgumentCaptor<VectorStoreDocumentDTO> vectorCaptor = ArgumentCaptor.forClass(VectorStoreDocumentDTO.class);
        verify(vectorStore).upsert(vectorCaptor.capture());
        assertThat(vectorCaptor.getValue().sourceType()).isEqualTo("memory");
        assertThat(vectorCaptor.getValue().vectorId()).isEqualTo("memory-88");
        assertThat(vectorCaptor.getValue().tenantId()).isEqualTo(1L);
        assertThat(vectorCaptor.getValue().applicationId()).isEqualTo(20001L);
        assertThat(vectorCaptor.getValue().ownerUserId()).isEqualTo(10001L);
        assertThat(vectorCaptor.getValue().profileId()).isEqualTo(50001L);
        assertThat(vectorCaptor.getValue().documentId()).isEqualTo(88L);
        assertThat(vectorCaptor.getValue().chunkId()).isEqualTo(88L);
        ArgumentCaptor<MemoryEntity> memoryCaptor = ArgumentCaptor.forClass(MemoryEntity.class);
        verify(memoryMapper).updateById(memoryCaptor.capture());
        assertThat(memoryCaptor.getValue().getMetadata().path("vector_index_status").asText()).isEqualTo("INDEXED");
        assertThat(memoryCaptor.getValue().getMetadata().path("embedding_model").asText()).isEqualTo("mock");
        assertThat(memoryCaptor.getValue().getMetadata().path("embedding_dimension").asInt()).isEqualTo(2);
    }

    @Test
    void recordKeepsPostgresMemoryWhenVectorIndexingFailsAndMarksFailure() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        VectorStore vectorStore = mock(VectorStore.class);
        DefaultMemoryWriteService vectorService = new DefaultMemoryWriteService(memoryMapper, embeddingService, vectorStore);
        when(memoryMapper.selectList(any())).thenReturn(List.of());
        org.mockito.Mockito.doAnswer(invocation -> {
            MemoryEntity entity = invocation.getArgument(0);
            assertThat(entity.getMetadata().path("vector_index_status").asText()).isEqualTo("PENDING");
            entity.setId(89L);
            return 1;
        }).when(memoryMapper).insert(any(MemoryEntity.class));
        when(embeddingService.embed("User likes reliable fallback"))
                .thenThrow(new IllegalStateException("embedding down"));

        vectorService.record(new RecordMemoryCommand(
                1L,
                10001L,
                20001L,
                50001L,
                "PREFERENCE",
                "User likes reliable fallback",
                90001L,
                "preference",
                List.of("reliability"),
                0.9,
                "preference"
        ));

        ArgumentCaptor<MemoryEntity> updateCaptor = ArgumentCaptor.forClass(MemoryEntity.class);
        verify(memoryMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getId()).isEqualTo(89L);
        assertThat(updateCaptor.getValue().getMetadata().path("vector_index_status").asText()).isEqualTo("FAILED");
        assertThat(updateCaptor.getValue().getMetadata().path("vector_index_error").asText()).contains("embedding down");
    }

    @Test
    void recordSkipsLongTermWriteWhenStrategyDoesNotAllowWrites() {
        service.record(new RecordMemoryCommand(
                1L,
                10001L,
                20001L,
                50001L,
                "PREFERENCE",
                "User prefers concise Chinese answers",
                90001L,
                "preference",
                List.of("style"),
                0.9,
                "preference",
                "READ_ONLY"
        ));

        verify(memoryMapper, never()).insert(any(MemoryEntity.class));
        verify(memoryMapper, never()).updateById(any(MemoryEntity.class));
    }

    @Test
    void recordBlocksExplicitDoNotRememberInstruction() {
        service.record(new RecordMemoryCommand(
                1L,
                10001L,
                20001L,
                50001L,
                "PREFERENCE",
                "不要记住我喜欢夜间部署",
                90001L,
                "preference",
                List.of("style"),
                0.9,
                "preference"
        ));

        verify(memoryMapper, never()).insert(any(MemoryEntity.class));
        verify(memoryMapper, never()).updateById(any(MemoryEntity.class));
    }

    @Test
    void recordSoftDisablesRelatedMemoriesWhenUserAsksToForget() {
        MemoryEntity existing = new MemoryEntity();
        existing.setId(77L);
        existing.setTenantId(1L);
        existing.setUserId(10001L);
        existing.setApplicationId(20001L);
        existing.setProfileId(50001L);
        existing.setMemoryType("PREFERENCE");
        existing.setMemoryCategory("preference");
        existing.setContent("User prefers night deployment.");
        existing.setStatus("ACTIVE");
        when(memoryMapper.selectList(any())).thenReturn(List.of(existing));

        service.record(new RecordMemoryCommand(
                1L,
                10001L,
                20001L,
                50001L,
                "SUMMARY",
                "请忘掉 night deployment 这个偏好",
                90001L,
                "summary",
                List.of("chat"),
                0.7,
                "task_memory"
        ));

        verify(memoryMapper, never()).insert(any(MemoryEntity.class));
        ArgumentCaptor<MemoryEntity> captor = ArgumentCaptor.forClass(MemoryEntity.class);
        verify(memoryMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(77L);
        assertThat(captor.getValue().getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void recordBlocksSensitiveSecrets() {
        service.record(new RecordMemoryCommand(
                1L,
                10001L,
                20001L,
                50001L,
                "FACT",
                "My api key is sk-1234567890abcdef and password is abc123456",
                90001L,
                "fact",
                List.of("secret"),
                0.9,
                "fact"
        ));

        verify(memoryMapper, never()).insert(any(MemoryEntity.class));
        verify(memoryMapper, never()).updateById(any(MemoryEntity.class));
    }
}
