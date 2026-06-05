package com.ls.agent.core.rag.api;

import com.ls.agent.core.rag.dto.VectorSearchQueryDTO;
import com.ls.agent.core.rag.dto.VectorSearchResultDTO;
import com.ls.agent.core.rag.dto.VectorStoreDocumentDTO;

import java.util.List;

public interface VectorStore {

    void upsert(VectorStoreDocumentDTO document);

    List<VectorSearchResultDTO> search(VectorSearchQueryDTO query);

    default int deleteByDocument(Long tenantId, Long applicationId, Long ownerUserId, Long profileId, Long documentId) {
        return deleteByDocument("rag", tenantId, applicationId, ownerUserId, profileId, documentId);
    }

    int deleteByDocument(
            String sourceType,
            Long tenantId,
            Long applicationId,
            Long ownerUserId,
            Long profileId,
            Long documentId
    );
}
