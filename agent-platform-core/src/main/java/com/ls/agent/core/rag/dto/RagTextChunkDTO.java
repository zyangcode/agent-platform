package com.ls.agent.core.rag.dto;

public record RagTextChunkDTO(
        String documentTitle,
        String sourceUri,
        String headingPath,
        int chunkIndex,
        String content,
        int tokenCount,
        String contentHash
) {

    public RagTextChunkDTO {
        documentTitle = documentTitle == null ? "" : documentTitle;
        sourceUri = sourceUri == null ? "" : sourceUri;
        headingPath = headingPath == null ? "" : headingPath;
        content = content == null ? "" : content.strip();
        tokenCount = Math.max(0, tokenCount);
        contentHash = contentHash == null ? "" : contentHash;
    }
}
