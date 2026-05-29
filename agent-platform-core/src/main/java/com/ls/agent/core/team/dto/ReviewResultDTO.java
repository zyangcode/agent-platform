package com.ls.agent.core.team.dto;

import java.util.List;

public record ReviewResultDTO(
        Boolean passed,
        List<ReviewIssueDTO> issues,
        List<String> retryTasks,
        String summary,
        Boolean replanRequired,
        String replanInstruction
) {
    public ReviewResultDTO {
        issues = issues == null ? List.of() : List.copyOf(issues);
        retryTasks = retryTasks == null ? List.of() : List.copyOf(retryTasks);
        replanRequired = Boolean.TRUE.equals(replanRequired);
    }

    public ReviewResultDTO(
            Boolean passed,
            List<ReviewIssueDTO> issues,
            List<String> retryTasks,
            String summary
    ) {
        this(passed, issues, retryTasks, summary, false, null);
    }

    public record ReviewIssueDTO(
            String taskId,
            String level,
            String message
    ) {
    }
}
