package com.ls.agent.core.rag;

import com.ls.agent.core.rag.application.DefaultPostgresRagEngine;
import com.ls.agent.core.rag.api.EmbeddingService;
import com.ls.agent.core.rag.api.VectorStore;
import com.ls.agent.core.rag.application.TextSplitter;
import com.ls.agent.core.rag.command.IngestKnowledgeDocumentCommand;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;
import com.ls.agent.core.rag.dto.RagIngestResultDTO;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;
import com.ls.agent.core.rag.dto.VectorSearchQueryDTO;
import com.ls.agent.core.rag.dto.VectorSearchResultDTO;
import com.ls.agent.core.rag.dto.VectorStoreDocumentDTO;
import com.ls.agent.core.rag.entity.KnowledgeChunkEntity;
import com.ls.agent.core.rag.entity.KnowledgeDocumentEntity;
import com.ls.agent.core.rag.mapper.KnowledgeChunkMapper;
import com.ls.agent.core.rag.mapper.KnowledgeDocumentMapper;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.FinishTraceSpanCommand;
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceSpanDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultPostgresRagEngineTest {

    private final KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
    private final KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final TraceService traceService = mock(TraceService.class);
    private final DefaultPostgresRagEngine engine = new DefaultPostgresRagEngine(
            documentMapper,
            chunkMapper,
            new TextSplitter()
    );

    @Test
    void ingestCreatesDocumentAndChunksWithScopeAndMetadata() {
        doAnswer(invocation -> {
            KnowledgeDocumentEntity document = invocation.getArgument(0);
            document.setId(90001L);
            return 1;
        }).when(documentMapper).insert(any(KnowledgeDocumentEntity.class));

        RagIngestResultDTO result = engine.ingest(new IngestKnowledgeDocumentCommand(
                1L,
                20001L,
                10001L,
                50001L,
                "Basketball Guide",
                "MANUAL",
                "kb://basketball",
                "# Outdoor\nOutdoor basketball is suitable after rain only when the court is dry.",
                40,
                5
        ));

        ArgumentCaptor<KnowledgeDocumentEntity> documentCaptor = ArgumentCaptor.forClass(KnowledgeDocumentEntity.class);
        verify(documentMapper).insert(documentCaptor.capture());
        KnowledgeDocumentEntity document = documentCaptor.getValue();
        assertThat(document.getTenantId()).isEqualTo(1L);
        assertThat(document.getApplicationId()).isEqualTo(20001L);
        assertThat(document.getOwnerUserId()).isEqualTo(10001L);
        assertThat(document.getProfileId()).isEqualTo(50001L);
        assertThat(document.getTitle()).isEqualTo("Basketball Guide");
        assertThat(document.getSourceType()).isEqualTo("MANUAL");
        assertThat(document.getSourceUri()).isEqualTo("kb://basketball");
        assertThat(document.getStatus()).isEqualTo("INDEXED");
        assertThat(document.getMetadata()).isNotNull();

        ArgumentCaptor<KnowledgeChunkEntity> chunkCaptor = ArgumentCaptor.forClass(KnowledgeChunkEntity.class);
        verify(chunkMapper).insert(chunkCaptor.capture());
        KnowledgeChunkEntity chunk = chunkCaptor.getValue();
        assertThat(chunk.getTenantId()).isEqualTo(1L);
        assertThat(chunk.getApplicationId()).isEqualTo(20001L);
        assertThat(chunk.getDocumentId()).isEqualTo(90001L);
        assertThat(chunk.getContent()).contains("Outdoor basketball");
        assertThat(chunk.getStatus()).isEqualTo("ACTIVE");
        assertThat(chunk.getMetadata().path("headingPath").asText()).contains("Outdoor");

        assertThat(result.documentId()).isEqualTo(90001L);
        assertThat(result.title()).isEqualTo("Basketball Guide");
        assertThat(result.chunkCount()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("INDEXED");
    }

    @Test
    void ingestUpsertsChunkVectorsWhenVectorStoreIsConfigured() {
        DefaultPostgresRagEngine vectorEngine = new DefaultPostgresRagEngine(
                documentMapper,
                chunkMapper,
                new TextSplitter(),
                embeddingService,
                vectorStore
        );
        doAnswer(invocation -> {
            KnowledgeDocumentEntity document = invocation.getArgument(0);
            document.setId(90001L);
            return 1;
        }).when(documentMapper).insert(any(KnowledgeDocumentEntity.class));
        doAnswer(invocation -> {
            KnowledgeChunkEntity chunk = invocation.getArgument(0);
            chunk.setId(91001L);
            return 1;
        }).when(chunkMapper).insert(any(KnowledgeChunkEntity.class));
        EmbeddingVectorDTO chunkVector = new EmbeddingVectorDTO("mock", new float[]{1.0f, 0.0f});
        when(embeddingService.embed("Outdoor basketball is suitable after rain only when the court is dry."))
                .thenReturn(chunkVector);

        vectorEngine.ingest(new IngestKnowledgeDocumentCommand(
                1L,
                20001L,
                10001L,
                50001L,
                "Basketball Guide",
                "MANUAL",
                "kb://basketball",
                "Outdoor basketball is suitable after rain only when the court is dry.",
                40,
                5
        ));

        ArgumentCaptor<VectorStoreDocumentDTO> vectorCaptor = ArgumentCaptor.forClass(VectorStoreDocumentDTO.class);
        verify(vectorStore).upsert(vectorCaptor.capture());
        VectorStoreDocumentDTO vectorDocument = vectorCaptor.getValue();
        assertThat(vectorDocument.vectorId()).isEqualTo("rag-chunk-91001");
        assertThat(vectorDocument.tenantId()).isEqualTo(1L);
        assertThat(vectorDocument.applicationId()).isEqualTo(20001L);
        assertThat(vectorDocument.ownerUserId()).isEqualTo(10001L);
        assertThat(vectorDocument.profileId()).isEqualTo(50001L);
        assertThat(vectorDocument.documentId()).isEqualTo(90001L);
        assertThat(vectorDocument.chunkId()).isEqualTo(91001L);
        assertThat(vectorDocument.vector()).isEqualTo(chunkVector);
        ArgumentCaptor<KnowledgeChunkEntity> updatedChunkCaptor = ArgumentCaptor.forClass(KnowledgeChunkEntity.class);
        verify(chunkMapper).updateById(updatedChunkCaptor.capture());
        assertThat(updatedChunkCaptor.getValue().getId()).isEqualTo(91001L);
        assertThat(updatedChunkCaptor.getValue().getVectorId()).isEqualTo("rag-chunk-91001");
    }

    @Test
    void ingestSameDocumentHashReplacesExistingChunks() {
        KnowledgeDocumentEntity existing = new KnowledgeDocumentEntity();
        existing.setId(90001L);
        when(documentMapper.selectActiveByScopeAndHash(
                1L,
                20001L,
                10001L,
                50001L,
                sha256("Policy content for refund.")
        )).thenReturn(existing);

        RagIngestResultDTO result = engine.ingest(new IngestKnowledgeDocumentCommand(
                1L,
                20001L,
                10001L,
                50001L,
                "Policy",
                "MANUAL",
                "kb://policy",
                "Policy content for refund.",
                40,
                5
        ));

        verify(documentMapper).updateById(any(KnowledgeDocumentEntity.class));
        verify(chunkMapper).disableByDocumentId(1L, 20001L, 90001L);
        assertThat(result.documentId()).isEqualTo(90001L);
    }

    @Test
    void ingestRecordsTraceSpanWhenTraceContextExists() {
        EmbeddingService traceObservedEmbeddingService = new TraceObservedEmbeddingService();
        VectorStore traceObservedVectorStore = new TraceObservedVectorStore();
        DefaultPostgresRagEngine tracedEngine = new DefaultPostgresRagEngine(
                documentMapper,
                chunkMapper,
                new TextSplitter(),
                traceObservedEmbeddingService,
                traceObservedVectorStore,
                traceService
        );
        when(traceService.startSpan(any(StartTraceSpanCommand.class)))
                .thenAnswer(invocation -> {
                    StartTraceSpanCommand command = invocation.getArgument(0);
                    return new TraceSpanDTO(30003L, command.traceId(), command.parentSpanId(), command.spanName(),
                            command.spanType(), command.component(), "RUNNING", LocalDateTime.now(), null,
                            null, null, null, command.attributes(), LocalDateTime.now());
                });
        doAnswer(invocation -> {
            KnowledgeDocumentEntity document = invocation.getArgument(0);
            document.setId(90001L);
            return 1;
        }).when(documentMapper).insert(any(KnowledgeDocumentEntity.class));
        doAnswer(invocation -> {
            KnowledgeChunkEntity chunk = invocation.getArgument(0);
            chunk.setId(91001L);
            return 1;
        }).when(chunkMapper).insert(any(KnowledgeChunkEntity.class));
        RagIngestResultDTO result = tracedEngine.ingest(new IngestKnowledgeDocumentCommand(
                1L,
                20001L,
                10001L,
                50001L,
                "Basketball Guide",
                "MANUAL",
                "kb://basketball",
                "Outdoor basketball is suitable after rain only when the court is dry.",
                40,
                5
        ), "trace-1", 42L);

        assertThat(result.chunkCount()).isEqualTo(1);
        ArgumentCaptor<StartTraceSpanCommand> startCaptor = ArgumentCaptor.forClass(StartTraceSpanCommand.class);
        verify(traceService).startSpan(startCaptor.capture());
        StartTraceSpanCommand start = startCaptor.getValue();
        assertThat(start.traceId()).isEqualTo("trace-1");
        assertThat(start.parentSpanId()).isEqualTo(42L);
        assertThat(start.spanName()).isEqualTo("rag.ingest");
        assertThat(start.component()).isEqualTo("core.rag");
        assertThat(start.attributes().path("titleChars").asInt()).isEqualTo("Basketball Guide".length());
        assertThat(start.attributes().path("sourceType").asText()).isEqualTo("MANUAL");
        assertThat(start.attributes().path("embeddingService").asText()).isEqualTo("TraceObservedEmbeddingService");
        assertThat(start.attributes().path("vectorStore").asText()).isEqualTo("TraceObservedVectorStore");

        ArgumentCaptor<FinishTraceSpanCommand> finishCaptor = ArgumentCaptor.forClass(FinishTraceSpanCommand.class);
        verify(traceService).finishSpan(finishCaptor.capture());
        FinishTraceSpanCommand finish = finishCaptor.getValue();
        assertThat(finish.status()).isEqualTo("SUCCESS");
        assertThat(finish.attributes().path("documentId").asLong()).isEqualTo(90001L);
        assertThat(finish.attributes().path("chunkCount").asInt()).isEqualTo(1);
        assertThat(finish.attributes().path("vectorIndexedCount").asInt()).isEqualTo(1);
    }

    @Test
    void searchMapsActiveChunksReturnedByScopedKeywordQuery() {
        KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
        chunk.setId(91001L);
        chunk.setDocumentId(90001L);
        chunk.setTitle("Basketball Guide");
        chunk.setSourceUri("kb://basketball");
        chunk.setContent("Outdoor basketball needs a dry court after rain.");
        chunk.setKeywordScore(2.0);
        when(chunkMapper.searchActiveChunks(
                eq(1L),
                eq(20001L),
                eq(10001L),
                eq(50001L),
                eq(List.of("outdoor", "basketball")),
                eq(5)
        )).thenReturn(List.of(chunk));

        List<RagSearchResultDTO> results = engine.search(
                1L,
                20001L,
                10001L,
                50001L,
                "outdoor basketball",
                5
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).documentId()).isEqualTo(90001L);
        assertThat(results.get(0).chunkId()).isEqualTo(91001L);
        assertThat(results.get(0).title()).isEqualTo("Basketball Guide");
        assertThat(results.get(0).sourceUri()).isEqualTo("kb://basketball");
        assertThat(results.get(0).score()).isEqualTo(2.0);
    }

    @Test
    void searchUsesVectorStoreAndHydratesChunksWhenVectorResultsExist() {
        DefaultPostgresRagEngine vectorEngine = new DefaultPostgresRagEngine(
                documentMapper,
                chunkMapper,
                new TextSplitter(),
                embeddingService,
                vectorStore
        );
        EmbeddingVectorDTO queryVector = new EmbeddingVectorDTO("mock", new float[]{1.0f, 0.0f});
        when(embeddingService.embed("basketball after rain")).thenReturn(queryVector);
        when(vectorStore.search(new VectorSearchQueryDTO(
                1L,
                20001L,
                10001L,
                50001L,
                queryVector,
                5
        ))).thenReturn(List.of(new VectorSearchResultDTO("vec-91001", 90001L, 91001L, 0.87)));
        KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
        chunk.setId(91001L);
        chunk.setDocumentId(90001L);
        chunk.setTitle("Basketball Guide");
        chunk.setSourceUri("kb://basketball");
        chunk.setContent("Outdoor basketball needs a dry court after rain.");
        when(chunkMapper.selectActiveChunksByIds(1L, 20001L, 10001L, 50001L, List.of(91001L)))
                .thenReturn(List.of(chunk));

        List<RagSearchResultDTO> results = vectorEngine.search(
                1L,
                20001L,
                10001L,
                50001L,
                "basketball after rain",
                5
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunkId()).isEqualTo(91001L);
        assertThat(results.get(0).content()).contains("dry court");
        assertThat(results.get(0).score()).isEqualTo(0.87);
    }

    @Test
    void searchRecordsEmbeddingAndVectorSearchTraceSpansWhenTraceContextExists() {
        VectorStore traceObservedVectorStore = new TraceObservedVectorStore();
        DefaultPostgresRagEngine vectorEngine = new DefaultPostgresRagEngine(
                documentMapper,
                chunkMapper,
                new TextSplitter(),
                embeddingService,
                traceObservedVectorStore,
                traceService
        );
        when(traceService.startSpan(any(StartTraceSpanCommand.class)))
                .thenAnswer(invocation -> {
                    StartTraceSpanCommand command = invocation.getArgument(0);
                    long id = "rag.embedding".equals(command.spanName()) ? 30001L : 30002L;
                    return new TraceSpanDTO(id, command.traceId(), command.parentSpanId(), command.spanName(),
                            command.spanType(), command.component(), "RUNNING", LocalDateTime.now(), null,
                            null, null, null, command.attributes(), LocalDateTime.now());
                });
        EmbeddingVectorDTO queryVector = new EmbeddingVectorDTO("mock", new float[]{1.0f, 0.0f});
        when(embeddingService.embed("basketball after rain")).thenReturn(queryVector);
        KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
        chunk.setId(91001L);
        chunk.setDocumentId(90001L);
        chunk.setTitle("Basketball Guide");
        chunk.setSourceUri("kb://basketball");
        chunk.setContent("Outdoor basketball needs a dry court after rain.");
        when(chunkMapper.selectActiveChunksByIds(1L, 20001L, 10001L, 50001L, List.of(91001L)))
                .thenReturn(List.of(chunk));

        List<RagSearchResultDTO> results = vectorEngine.search(
                1L,
                20001L,
                10001L,
                50001L,
                "basketball after rain",
                5,
                "trace-1",
                42L
        );

        assertThat(results).hasSize(1);
        ArgumentCaptor<StartTraceSpanCommand> startCaptor = ArgumentCaptor.forClass(StartTraceSpanCommand.class);
        verify(traceService, org.mockito.Mockito.times(2)).startSpan(startCaptor.capture());
        assertThat(startCaptor.getAllValues())
                .extracting(StartTraceSpanCommand::spanName)
                .containsExactly("rag.embedding", "rag.vector.search");
        assertThat(startCaptor.getAllValues())
                .allSatisfy(command -> {
                    assertThat(command.traceId()).isEqualTo("trace-1");
                    assertThat(command.parentSpanId()).isEqualTo(42L);
                    assertThat(command.component()).isEqualTo("core.rag");
                });
        assertThat(startCaptor.getAllValues().get(0).attributes().path("queryChars").asInt()).isEqualTo("basketball after rain".length());
        assertThat(startCaptor.getAllValues().get(1).attributes().path("topK").asInt()).isEqualTo(5);
        assertThat(startCaptor.getAllValues().get(1).attributes().path("vectorStore").asText()).isEqualTo("TraceObservedVectorStore");
        ArgumentCaptor<FinishTraceSpanCommand> finishCaptor = ArgumentCaptor.forClass(FinishTraceSpanCommand.class);
        verify(traceService, org.mockito.Mockito.times(2)).finishSpan(finishCaptor.capture());
        assertThat(finishCaptor.getAllValues())
                .extracting(FinishTraceSpanCommand::status)
                .containsExactly("SUCCESS", "SUCCESS");
        assertThat(finishCaptor.getAllValues().get(0).attributes().path("dimension").asInt()).isEqualTo(2);
        assertThat(finishCaptor.getAllValues().get(1).attributes().path("resultCount").asInt()).isEqualTo(1);
    }

    @Test
    void searchFallsBackToKeywordWhenVectorStoreReturnsNoResults() {
        DefaultPostgresRagEngine vectorEngine = new DefaultPostgresRagEngine(
                documentMapper,
                chunkMapper,
                new TextSplitter(),
                embeddingService,
                vectorStore
        );
        when(embeddingService.embed("outdoor basketball")).thenReturn(new EmbeddingVectorDTO("mock", new float[]{1.0f}));
        when(vectorStore.search(any(VectorSearchQueryDTO.class))).thenReturn(List.of());
        KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
        chunk.setId(91001L);
        chunk.setDocumentId(90001L);
        chunk.setTitle("Basketball Guide");
        chunk.setSourceUri("kb://basketball");
        chunk.setContent("Outdoor basketball needs a dry court after rain.");
        chunk.setKeywordScore(2.0);
        when(chunkMapper.searchActiveChunks(
                eq(1L),
                eq(20001L),
                eq(10001L),
                eq(50001L),
                eq(List.of("outdoor", "basketball")),
                eq(5)
        )).thenReturn(List.of(chunk));

        List<RagSearchResultDTO> results = vectorEngine.search(
                1L,
                20001L,
                10001L,
                50001L,
                "outdoor basketball",
                5
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).score()).isEqualTo(2.0);
    }

    @Test
    void deleteOnlyDisablesDocumentAndChunksWhenScopeMatches() {
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setId(90001L);
        when(documentMapper.selectActiveByIdAndScope(1L, 20001L, 10001L, 50001L, 90001L))
                .thenReturn(document);

        int deleted = engine.delete(1L, 20001L, 10001L, 50001L, 90001L);

        verify(documentMapper).disableById(90001L);
        verify(chunkMapper).disableByDocumentId(1L, 20001L, 90001L);
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void deleteStillSucceedsWhenVectorCleanupFails() {
        DefaultPostgresRagEngine vectorEngine = new DefaultPostgresRagEngine(
                documentMapper,
                chunkMapper,
                new TextSplitter(),
                embeddingService,
                vectorStore
        );
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setId(90001L);
        when(documentMapper.selectActiveByIdAndScope(1L, 20001L, 10001L, 50001L, 90001L))
                .thenReturn(document);
        doThrow(new IllegalStateException("qdrant delete failed"))
                .when(vectorStore)
                .deleteByDocument(1L, 20001L, 10001L, 50001L, 90001L);

        int deleted = vectorEngine.delete(1L, 20001L, 10001L, 50001L, 90001L);

        verify(documentMapper).disableById(90001L);
        verify(chunkMapper).disableByDocumentId(1L, 20001L, 90001L);
        assertThat(deleted).isEqualTo(1);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static final class TraceObservedEmbeddingService implements EmbeddingService {

        @Override
        public EmbeddingVectorDTO embed(String text) {
            return new EmbeddingVectorDTO("mock", new float[]{1.0f, 0.0f});
        }
    }

    private static final class TraceObservedVectorStore implements VectorStore {

        @Override
        public void upsert(VectorStoreDocumentDTO document) {
        }

        @Override
        public List<VectorSearchResultDTO> search(VectorSearchQueryDTO query) {
            return List.of(new VectorSearchResultDTO("vec-91001", 90001L, 91001L, 0.87));
        }

        @Override
        public int deleteByDocument(
                String sourceType,
                Long tenantId,
                Long applicationId,
                Long ownerUserId,
                Long profileId,
                Long documentId
        ) {
            return 0;
        }
    }
}
