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
        EmbeddingVectorDTO vector
) {

    public VectorStoreDocumentDTO {
        sourceType = sourceType == null || sourceType.isBlank() ? "rag" : sourceType.strip().toLowerCase();
        vectorId = vectorId == null ? "" : vectorId;
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
        this("rag", vectorId, tenantId, applicationId, ownerUserId, profileId, documentId, chunkId, vector);
    }
}
