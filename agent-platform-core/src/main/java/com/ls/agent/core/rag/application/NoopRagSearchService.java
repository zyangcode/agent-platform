package com.ls.agent.core.rag.application;

import com.ls.agent.core.rag.api.RagSearchService;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;

import java.util.List;

public class NoopRagSearchService implements RagSearchService {

    @Override
    public List<RagSearchResultDTO> search(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            int topK
    ) {
        return List.of();
    }
}
