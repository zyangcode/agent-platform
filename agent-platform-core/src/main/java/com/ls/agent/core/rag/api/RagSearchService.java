package com.ls.agent.core.rag.api;

import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;

import java.util.List;

public interface RagSearchService {

    List<RagSearchResultDTO> search(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            int topK
    );

    default List<RagSearchResultDTO> search(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            int topK,
            String traceId,
            Long parentSpanId
    ) {
        return search(tenantId, applicationId, userId, profileId, query, topK);
    }

    default List<RagSearchResultDTO> search(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            int topK,
            EmbeddingVectorDTO queryVector,
            String traceId,
            Long parentSpanId
    ) {
        return search(tenantId, applicationId, userId, profileId, query, topK, traceId, parentSpanId);
    }
}
