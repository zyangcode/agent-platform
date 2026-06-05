package com.ls.agent.core.rag.application;

import com.ls.agent.core.rag.api.VectorStore;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;
import com.ls.agent.core.rag.dto.VectorSearchQueryDTO;
import com.ls.agent.core.rag.dto.VectorSearchResultDTO;
import com.ls.agent.core.rag.dto.VectorStoreDocumentDTO;

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.List;

public class MockVectorStore implements VectorStore {

    private final ConcurrentMap<String, VectorStoreDocumentDTO> documents = new ConcurrentHashMap<>();

    @Override
    public void upsert(VectorStoreDocumentDTO document) {
        if (document == null || document.vectorId().isBlank()) {
            return;
        }
        documents.put(document.vectorId(), document);
    }

    @Override
    public List<VectorSearchResultDTO> search(VectorSearchQueryDTO query) {
        if (query == null || query.topK() <= 0 || isEmpty(query.queryVector())) {
            return List.of();
        }
        return documents.values().stream()
                .filter(document -> matchesScope(document, query))
                .map(document -> new VectorSearchResultDTO(
                        document.vectorId(),
                        document.documentId(),
                        document.chunkId(),
                        dot(query.queryVector(), document.vector())
                ))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingDouble(VectorSearchResultDTO::score).reversed()
                        .thenComparing(VectorSearchResultDTO::vectorId))
                .limit(query.topK())
                .toList();
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
        int before = documents.size();
        String normalizedSourceType = normalizeSourceType(sourceType);
        documents.entrySet().removeIf(entry -> {
            VectorStoreDocumentDTO document = entry.getValue();
            return Objects.equals(document.tenantId(), tenantId)
                    && Objects.equals(document.sourceType(), normalizedSourceType)
                    && Objects.equals(document.applicationId(), applicationId)
                    && Objects.equals(document.ownerUserId(), ownerUserId)
                    && Objects.equals(document.profileId(), profileId)
                    && Objects.equals(document.documentId(), documentId);
        });
        return before - documents.size();
    }

    private boolean matchesScope(VectorStoreDocumentDTO document, VectorSearchQueryDTO query) {
        return Objects.equals(document.tenantId(), query.tenantId())
                && Objects.equals(document.sourceType(), query.sourceType())
                && Objects.equals(document.applicationId(), query.applicationId())
                && Objects.equals(document.ownerUserId(), query.ownerUserId())
                && (document.profileId() == null || Objects.equals(document.profileId(), query.profileId()));
    }

    private String normalizeSourceType(String sourceType) {
        return sourceType == null || sourceType.isBlank() ? "rag" : sourceType.strip().toLowerCase();
    }

    private boolean isEmpty(EmbeddingVectorDTO vector) {
        return vector == null || vector.values().length == 0;
    }

    private double dot(EmbeddingVectorDTO left, EmbeddingVectorDTO right) {
        if (left == null || right == null) {
            return 0.0;
        }
        float[] leftValues = left.values();
        float[] rightValues = right.values();
        int length = Math.min(leftValues.length, rightValues.length);
        double score = 0.0;
        for (int i = 0; i < length; i++) {
            score += leftValues[i] * rightValues[i];
        }
        return score;
    }
}
