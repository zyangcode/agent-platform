package com.ls.agent.core.team.application;

import com.ls.agent.common.error.BizException;
import com.ls.agent.core.team.dto.ExecutionResultDTO;
import com.ls.agent.core.team.dto.ReviewResultDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewResultValidatorTest {

    private final ReviewResultValidator validator = new ReviewResultValidator();

    @Test
    void acceptsPassedReviewWithoutRetryTasks() {
        ReviewResultDTO review = new ReviewResultDTO(
                true,
                List.of(),
                List.of(),
                "Looks good"
        );

        assertThatCode(() -> validator.validate(review, executionResults()))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsFailedReviewWithExistingRetryTask() {
        ReviewResultDTO review = new ReviewResultDTO(
                false,
                List.of(new ReviewResultDTO.ReviewIssueDTO("task-1", "WARN", "Need detail")),
                List.of("task-1"),
                "Retry task-1"
        );

        assertThatCode(() -> validator.validate(review, executionResults()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNullReview() {
        assertThatThrownBy(() -> validator.validate(null, executionResults()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("review result is required");
    }

    @Test
    void rejectsMissingPassed() {
        ReviewResultDTO review = new ReviewResultDTO(null, List.of(), List.of(), "Missing passed");

        assertThatThrownBy(() -> validator.validate(review, executionResults()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("passed is required");
    }

    @Test
    void rejectsRetryTaskThatDoesNotExist() {
        ReviewResultDTO review = new ReviewResultDTO(false, List.of(), List.of("missing-task"), "Retry missing");

        assertThatThrownBy(() -> validator.validate(review, executionResults()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("retryTasks references missing task missing-task");
    }

    @Test
    void rejectsInvalidIssueLevel() {
        ReviewResultDTO review = new ReviewResultDTO(
                false,
                List.of(new ReviewResultDTO.ReviewIssueDTO("task-1", "BAD", "Invalid level")),
                List.of(),
                "Invalid"
        );

        assertThatThrownBy(() -> validator.validate(review, executionResults()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("issue level must be INFO, WARN, or ERROR");
    }

    private List<ExecutionResultDTO> executionResults() {
        return List.of(
                new ExecutionResultDTO("task-1", "MODEL_TASK", "SUCCESS", "ok", List.of(), null),
                new ExecutionResultDTO("task-2", "TOOL_TASK", "FAILED", "", List.of("weather"), "tool down")
        );
    }
}
