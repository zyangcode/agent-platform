package com.ls.agent.core.memory.api;

import com.ls.agent.core.memory.command.UpdateMemoryCommand;
import com.ls.agent.core.memory.dto.MemoryRecordDTO;

import java.util.List;

public interface MemoryManagementService {

    List<MemoryRecordDTO> list(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String category,
            String query,
            int limit
    );

    MemoryRecordDTO update(UpdateMemoryCommand command);

    int disable(Long tenantId, Long applicationId, Long userId, Long profileId, Long memoryId);
}
