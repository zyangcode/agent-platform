package com.ls.agent.core.team.dto;

import java.util.List;

public record ExecutionResultDTO(
        String taskId,
        String taskType,
        String status,
        String result,
        List<String> usedTools,
        String errorMessage
) {
    public ExecutionResultDTO {
        usedTools = usedTools == null ? List.of() : List.copyOf(usedTools);
    }
}
