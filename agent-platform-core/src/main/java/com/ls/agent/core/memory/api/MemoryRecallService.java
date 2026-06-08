package com.ls.agent.core.memory.api;

import com.ls.agent.core.memory.dto.MemoryDTO;
import com.ls.agent.core.memory.dto.MemoryRecallFilter;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;

import java.util.List;

public interface MemoryRecallService {

    List<MemoryDTO> recall(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            int limit
    );

    List<MemoryDTO> recall(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            MemoryRecallFilter filter
    );

    default List<MemoryDTO> recall(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            MemoryRecallFilter filter,
            String traceId,
            Long parentSpanId
    ) {
        return recall(tenantId, applicationId, userId, profileId, query, filter);
    }

    default List<MemoryDTO> recall(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String query,
            MemoryRecallFilter filter,
            EmbeddingVectorDTO queryVector,
            String traceId,
            Long parentSpanId
    ) {
        return recall(tenantId, applicationId, userId, profileId, query, filter, traceId, parentSpanId);
    }
}
