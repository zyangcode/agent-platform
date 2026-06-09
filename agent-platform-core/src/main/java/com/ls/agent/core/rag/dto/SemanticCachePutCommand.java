package com.ls.agent.core.rag.dto;

import java.util.List;

public record SemanticCachePutCommand(
        Long tenantId,
        Long applicationId,
        Long ownerUserId,
        Long profileId,
        String query,
        EmbeddingVectorDTO queryVector,
        int topK,
        List<RagSearchResultDTO> results
) {

    public SemanticCachePutCommand {
        query = query == null ? "" : query.strip();
        topK = Math.max(0, topK);
        results = results == null ? List.of() : List.copyOf(results);
    }
}
