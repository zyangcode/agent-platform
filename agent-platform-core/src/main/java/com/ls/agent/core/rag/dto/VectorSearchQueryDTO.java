package com.ls.agent.core.rag.dto;

public record VectorSearchQueryDTO(
        String sourceType,
        Long tenantId,
        Long applicationId,
        Long ownerUserId,
        Long profileId,
        EmbeddingVectorDTO queryVector,
        int topK
) {

    public VectorSearchQueryDTO {
        sourceType = sourceType == null || sourceType.isBlank() ? "rag" : sourceType.strip().toLowerCase();
        topK = Math.max(0, topK);
    }

    public VectorSearchQueryDTO(
            Long tenantId,
            Long applicationId,
            Long ownerUserId,
            Long profileId,
            EmbeddingVectorDTO queryVector,
            int topK
    ) {
        this("rag", tenantId, applicationId, ownerUserId, profileId, queryVector, topK);
    }
}
