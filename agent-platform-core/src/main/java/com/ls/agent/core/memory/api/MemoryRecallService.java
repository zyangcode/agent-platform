package com.ls.agent.core.memory.api;

import com.ls.agent.core.memory.dto.MemoryDTO;

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
}
