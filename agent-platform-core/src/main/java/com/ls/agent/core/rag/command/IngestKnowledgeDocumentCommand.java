package com.ls.agent.core.rag.command;

public record IngestKnowledgeDocumentCommand(
        Long tenantId,
        Long applicationId,
        Long ownerUserId,
        Long profileId,
        String title,
        String sourceType,
        String sourceUri,
        String content,
        int chunkTokenBudget,
        int overlapTokens
) {

    public IngestKnowledgeDocumentCommand {
        title = title == null ? "" : title.strip();
        sourceType = sourceType == null ? "" : sourceType.strip();
        sourceUri = sourceUri == null ? "" : sourceUri.strip();
        content = content == null ? "" : content;
        chunkTokenBudget = chunkTokenBudget <= 0 ? 400 : chunkTokenBudget;
        overlapTokens = Math.max(0, overlapTokens);
    }
}
