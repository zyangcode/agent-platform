package com.ls.agent.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateMemoryRequest(
        @NotNull Long applicationId,
        Long profileId,
        String content,
        String memoryCategory,
        List<String> tags,
        Double importance,
        String slotHint,
        Boolean pinned
) {

    public UpdateMemoryRequest {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
