package com.ls.agent.core.team.dto;

import java.util.List;

public record ReviewResultDTO(
        Boolean passed,
        List<ReviewIssueDTO> issues,
        List<String> retryTasks,
        String summary
) {
    public ReviewResultDTO {
        issues = issues == null ? List.of() : List.copyOf(issues);
        retryTasks = retryTasks == null ? List.of() : List.copyOf(retryTasks);
    }

    public record ReviewIssueDTO(
            String taskId,
            String level,
            String message
    ) {
    }
}
