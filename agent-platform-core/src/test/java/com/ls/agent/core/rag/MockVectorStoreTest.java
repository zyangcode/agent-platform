package com.ls.agent.core.rag;

import com.ls.agent.core.rag.application.MockEmbeddingService;
import com.ls.agent.core.rag.application.MockVectorStore;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;
import com.ls.agent.core.rag.dto.VectorSearchQueryDTO;
import com.ls.agent.core.rag.dto.VectorSearchResultDTO;
import com.ls.agent.core.rag.dto.VectorStoreDocumentDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockVectorStoreTest {

    private final MockEmbeddingService embeddingService = new MockEmbeddingService();
    private final MockVectorStore vectorStore = new MockVectorStore();

    @Test
    void searchReturnsNearestChunksWithinScope() {
        vectorStore.upsert(point("vec-1", 1L, 20001L, 10001L, 50001L, 90001L, 91001L,
                "Outdoor basketball needs dry court after rain"));
        vectorStore.upsert(point("vec-2", 1L, 20001L, 10001L, 60001L, 90002L, 91002L,
                "Refund policy answers within three business days"));
        EmbeddingVectorDTO queryVector = embeddingService.embed("basketball rain court");

        List<VectorSearchResultDTO> results = vectorStore.search(new VectorSearchQueryDTO(
                1L,
                20001L,
                10001L,
                50001L,
                queryVector,
                5
        ));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).documentId()).isEqualTo(90001L);
        assertThat(results.get(0).chunkId()).isEqualTo(91001L);
        assertThat(results.get(0).score()).isGreaterThan(0);
    }

    @Test
    void deleteByDocumentRemovesOnlyMatchingScope() {
        vectorStore.upsert(point("vec-1", 1L, 20001L, 10001L, 50001L, 90001L, 91001L,
                "Outdoor basketball"));
        vectorStore.upsert(point("vec-2", 1L, 20001L, 10001L, 60001L, 90001L, 91002L,
                "Outdoor basketball other profile"));

        int deleted = vectorStore.deleteByDocument(1L, 20001L, 10001L, 50001L, 90001L);

        assertThat(deleted).isEqualTo(1);
        assertThat(vectorStore.search(new VectorSearchQueryDTO(
                1L,
                20001L,
                10001L,
                50001L,
                embeddingService.embed("basketball"),
                5
        ))).isEmpty();
        assertThat(vectorStore.search(new VectorSearchQueryDTO(
                1L,
                20001L,
                10001L,
                60001L,
                embeddingService.embed("basketball"),
                5
        ))).hasSize(1);
    }

    @Test
    void deleteByDocumentRemovesOnlyMatchingSourceType() {
        vectorStore.upsert(point("rag-vec-1", "rag", 1L, 20001L, 10001L, 50001L, 88L, 91001L,
                "Basketball court policy"));
        vectorStore.upsert(point("memory-88", "memory", 1L, 20001L, 10001L, 50001L, 88L, 88L,
                "User likes basketball"));

        int deleted = vectorStore.deleteByDocument("memory", 1L, 20001L, 10001L, 50001L, 88L);

        assertThat(deleted).isEqualTo(1);
        assertThat(vectorStore.search(new VectorSearchQueryDTO(
                "memory",
                1L,
                20001L,
                10001L,
                50001L,
                embeddingService.embed("basketball"),
                5
        ))).isEmpty();
        assertThat(vectorStore.search(new VectorSearchQueryDTO(
                "rag",
                1L,
                20001L,
                10001L,
                50001L,
                embeddingService.embed("basketball"),
                5
        ))).extracting(VectorSearchResultDTO::vectorId).containsExactly("rag-vec-1");
    }

    @Test
    void searchKeepsMemoryAndRagVectorsSeparatedBySourceType() {
        vectorStore.upsert(point("rag-vec-1", "rag", 1L, 20001L, 10001L, 50001L, 90001L, 91001L,
                "Outdoor basketball"));
        vectorStore.upsert(point("memory-88", "memory", 1L, 20001L, 10001L, 50001L, 88L, 88L,
                "User likes basketball"));

        assertThat(vectorStore.search(new VectorSearchQueryDTO(
                1L,
                20001L,
                10001L,
                50001L,
                embeddingService.embed("basketball"),
                5
        ))).extracting(VectorSearchResultDTO::vectorId).containsExactly("rag-vec-1");

        assertThat(vectorStore.search(new VectorSearchQueryDTO(
                "memory",
                1L,
                20001L,
                10001L,
                50001L,
                embeddingService.embed("basketball"),
                5
        ))).extracting(VectorSearchResultDTO::vectorId).containsExactly("memory-88");
    }

    private VectorStoreDocumentDTO point(
            String vectorId,
            Long tenantId,
            Long applicationId,
            Long ownerUserId,
            Long profileId,
            Long documentId,
            Long chunkId,
            String content
    ) {
        return new VectorStoreDocumentDTO(
                vectorId,
                tenantId,
                applicationId,
                ownerUserId,
                profileId,
                documentId,
                chunkId,
                embeddingService.embed(content)
        );
    }

    private VectorStoreDocumentDTO point(
            String vectorId,
            String sourceType,
            Long tenantId,
            Long applicationId,
            Long ownerUserId,
            Long profileId,
            Long documentId,
            Long chunkId,
            String content
    ) {
        return new VectorStoreDocumentDTO(
                sourceType,
                vectorId,
                tenantId,
                applicationId,
                ownerUserId,
                profileId,
                documentId,
                chunkId,
                embeddingService.embed(content)
        );
    }
}
