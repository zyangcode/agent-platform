package com.ls.agent.core.rag.api;

import com.ls.agent.core.rag.dto.RagSearchResultDTO;

import java.util.List;

@FunctionalInterface
public interface RetrievalReranker {

    List<RagSearchResultDTO> rerank(String query, List<RagSearchResultDTO> candidates, int topK);

    static RetrievalReranker noop() {
        return (query, candidates, topK) -> {
            if (candidates == null || candidates.isEmpty() || topK <= 0) {
                return List.of();
            }
            return candidates.stream()
                    .limit(Math.max(1, topK))
                    .toList();
        };
    }
}
