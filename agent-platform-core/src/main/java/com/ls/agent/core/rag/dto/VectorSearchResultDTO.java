package com.ls.agent.core.rag.dto;

public record VectorSearchResultDTO(
        String vectorId,
        Long documentId,
        Long chunkId,
        double score
) {

    public VectorSearchResultDTO {
        vectorId = vectorId == null ? "" : vectorId;
    }
}
