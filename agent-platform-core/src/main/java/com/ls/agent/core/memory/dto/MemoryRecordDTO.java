package com.ls.agent.core.memory.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MemoryRecordDTO(
        Long id,
        Long applicationId,
        Long profileId,
        String memoryType,
        String memoryCategory,
        String content,
        List<String> tags,
        double importance,
        double confidence,
        int accessCount,
        LocalDateTime lastAccessedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String slotHint,
        String status
) {

    public MemoryRecordDTO {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
