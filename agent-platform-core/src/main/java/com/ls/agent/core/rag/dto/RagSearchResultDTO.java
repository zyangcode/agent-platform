package com.ls.agent.core.rag.dto;

public record RagSearchResultDTO(
        Long documentId,
        Long chunkId,
        String title,
        String content,
        String sourceUri,
        double score
) {

    public RagSearchResultDTO {
        title = title == null ? "" : title;
        content = content == null ? "" : content;
        sourceUri = sourceUri == null ? "" : sourceUri;
    }
}
