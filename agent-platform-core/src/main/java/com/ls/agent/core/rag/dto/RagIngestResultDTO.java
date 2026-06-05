package com.ls.agent.core.rag.dto;

public record RagIngestResultDTO(
        Long documentId,
        String title,
        String docHash,
        int chunkCount,
        String status
) {

    public RagIngestResultDTO {
        title = title == null ? "" : title;
        docHash = docHash == null ? "" : docHash;
        status = status == null ? "INDEXED" : status;
        chunkCount = Math.max(0, chunkCount);
    }
}
