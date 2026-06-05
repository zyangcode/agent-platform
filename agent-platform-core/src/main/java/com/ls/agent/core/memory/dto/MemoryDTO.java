package com.ls.agent.core.memory.dto;

import java.util.List;

public record MemoryDTO(
        String memoryType,
        String content,
        String memoryCategory,
        List<String> tags,
        double importance,
        String slotHint
) {

    public MemoryDTO(String memoryType, String content) {
        this(memoryType, content, null, List.of(), 0.5, null);
    }

    public MemoryDTO {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
