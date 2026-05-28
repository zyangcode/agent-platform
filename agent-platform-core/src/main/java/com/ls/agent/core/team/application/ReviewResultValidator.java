package com.ls.agent.core.team.application;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.team.dto.ExecutionResultDTO;
import com.ls.agent.core.team.dto.ReviewResultDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ReviewResultValidator {

    private static final Set<String> ISSUE_LEVELS = Set.of("INFO", "WARN", "ERROR");

    public void validate(ReviewResultDTO review, List<ExecutionResultDTO> executionResults) {
        if (review == null) {
            fail("review result is required");
        }
        if (review.passed() == null) {
            fail("passed is required");
        }
        Set<String> taskIds = taskIds(executionResults);
        for (String retryTask : review.retryTasks()) {
            if (retryTask == null || retryTask.isBlank() || !taskIds.contains(retryTask.trim())) {
                fail("retryTasks references missing task " + retryTask);
            }
        }
        for (ReviewResultDTO.ReviewIssueDTO issue : review.issues()) {
            if (issue == null) {
                fail("issue is required");
            }
            if (issue.level() == null || !ISSUE_LEVELS.contains(issue.level().trim())) {
                fail("issue level must be INFO, WARN, or ERROR");
            }
            if (issue.taskId() != null && !issue.taskId().isBlank() && !taskIds.contains(issue.taskId().trim())) {
                fail("issue taskId references missing task " + issue.taskId());
            }
            if (issue.message() == null || issue.message().isBlank()) {
                fail("issue message is required");
            }
        }
    }

    private Set<String> taskIds(List<ExecutionResultDTO> executionResults) {
        if (executionResults == null) {
            return Set.of();
        }
        return executionResults.stream()
                .map(ExecutionResultDTO::taskId)
                .filter(taskId -> taskId != null && !taskId.isBlank())
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void fail(String message) {
        throw new BizException(ErrorCode.REQUEST_INVALID, message);
    }
}
