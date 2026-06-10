package com.ls.agent.core.rag.dto;

public record VectorStoreDocumentDTO(
        String sourceType,
        String vectorId,
        Long tenantId,
        Long applicationId,
        Long ownerUserId,
        Long profileId,
        Long documentId,
        Long chunkId,
        EmbeddingVectorDTO vector,
        String memoryScope,
        Long sourceConversationId
) {

    public VectorStoreDocumentDTO {
        sourceType = sourceType == null || sourceType.isBlank() ? "rag" : sourceType.strip().toLowerCase();
        vectorId = vectorId == null ? "" : vectorId;
        memoryScope = memoryScope == null || memoryScope.isBlank() ? null : memoryScope.strip().toUpperCase(java.util.Locale.ROOT);
    }

    public VectorStoreDocumentDTO(
            String sourceType,
            String vectorId,
            Long tenantId,
            Long applicationId,
            Long ownerUserId,
            Long profileId,
            Long documentId,
            Long chunkId,
            EmbeddingVectorDTO vector
    ) {
        this(sourceType, vectorId, tenantId, applicationId, ownerUserId, profileId, documentId, chunkId, vector, null, null);
    }

    public VectorStoreDocumentDTO(
            String vectorId,
            Long tenantId,
            Long applicationId,
            Long ownerUserId,
            Long profileId,
            Long documentId,
            Long chunkId,
            EmbeddingVectorDTO vector
    ) {
        this("rag", vectorId, tenantId, applicationId, ownerUserId, profileId, documentId, chunkId, vector, null, null);
    }
}
