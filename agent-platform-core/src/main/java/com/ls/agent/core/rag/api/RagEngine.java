package com.ls.agent.core.rag.api;

import com.ls.agent.core.rag.command.IngestKnowledgeDocumentCommand;
import com.ls.agent.core.rag.dto.RagIngestResultDTO;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;

import java.util.List;

public interface RagEngine extends RagSearchService {

    RagIngestResultDTO ingest(IngestKnowledgeDocumentCommand command);

    default RagIngestResultDTO ingest(
            IngestKnowledgeDocumentCommand command,
            String traceId,
            Long parentSpanId
    ) {
        return ingest(command);
    }

    int delete(Long tenantId, Long applicationId, Long userId, Long profileId, Long documentId);

    @Override
    List<RagSearchResultDTO> search(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            int topK
    );
}
