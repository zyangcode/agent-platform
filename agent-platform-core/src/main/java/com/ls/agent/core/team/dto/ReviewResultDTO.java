package com.ls.agent.core.team.dto;

import java.util.List;

public record ReviewResultDTO(
        Boolean passed,
        List<ReviewIssueDTO> issues,
        List<String> retryTasks,
        String summary
) {
    public record ReviewIssueDTO(
            String taskId,
            String level,
            String message
    ) {
    }
}
