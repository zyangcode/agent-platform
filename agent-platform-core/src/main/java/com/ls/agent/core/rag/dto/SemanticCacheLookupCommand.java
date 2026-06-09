package com.ls.agent.core.rag.dto;

public record SemanticCacheLookupCommand(
        Long tenantId,
        Long applicationId,
        Long ownerUserId,
        Long profileId,
        String query,
        EmbeddingVectorDTO queryVector,
        int topK
) {

    public SemanticCacheLookupCommand {
        query = query == null ? "" : query.strip();
        topK = Math.max(0, topK);
    }
}
