package com.ls.agent.core.team.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.team.api.TeamReviewer;
import com.ls.agent.core.team.command.ReviewTeamCommand;
import com.ls.agent.core.team.dto.ExecutionResultDTO;
import com.ls.agent.core.team.dto.ReviewResultDTO;
import com.ls.agent.core.team.dto.TaskPlanDTO;
import com.ls.agent.core.team.dto.TeamReviewResultDTO;
import com.ls.agent.core.team.dto.TeamTaskDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DefaultTeamReviewer implements TeamReviewer {

    private static final int MAX_MODEL_ATTEMPTS = 2;
    private static final int FAILURE_PROMPT_MAX_LENGTH = 200;
    private static final BigDecimal REVIEW_TEMPERATURE = BigDecimal.valueOf(0.2);

    private final ModelInvokeService modelInvokeService;
    private final ReviewResultValidator reviewResultValidator;
    private final ObjectMapper objectMapper;

    public DefaultTeamReviewer(
            ModelInvokeService modelInvokeService,
            ReviewResultValidator reviewResultValidator,
            ObjectMapper objectMapper
    ) {
        this.modelInvokeService = modelInvokeService;
        this.reviewResultValidator = reviewResultValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public TeamReviewResultDTO review(ReviewTeamCommand command) {
        validate(command);
        String lastFailure = null;
        List<ModelInvokeResult> modelInvocations = new ArrayList<>();
        for (int attempt = 1; attempt <= MAX_MODEL_ATTEMPTS; attempt++) {
            try {
                ModelInvokeResult modelResult = invokeModel(command, lastFailure);
                if (modelResult != null) {
                    modelInvocations.add(modelResult);
                }
                ReviewResultDTO review = parseReview(modelResult);
                reviewResultValidator.validate(review, command.executionResults());
                return new TeamReviewResultDTO(review, modelInvocations);
            } catch (Exception ex) {
                lastFailure = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            }
        }
        return new TeamReviewResultDTO(fallbackReview(command, lastFailure), modelInvocations);
    }

    private ModelInvokeResult invokeModel(ReviewTeamCommand command, String previousFailure) {
        return modelInvokeService.invoke(new ModelInvokeCommand(
                command.context().modelConfigId(),
                messages(command, previousFailure),
                REVIEW_TEMPERATURE,
                false
        ));
    }

    private ReviewResultDTO parseReview(ModelInvokeResult result) {
        String content = result == null ? null : result.assistantMessage();
        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "reviewer response is empty");
        }
        try {
            return objectMapper.readValue(extractJson(content), ReviewResultDTO.class);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "reviewer response is not valid ReviewResult JSON");
        }
    }

    private List<ModelMessage> messages(ReviewTeamCommand command, String previousFailure) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Original user request:\n")
                .append(safe(command.userInput()))
                .append("\n\nProfile prompt:\n")
                .append(profilePrompt(command))
                .append("\n\nTask plan:\n")
                .append(planSummary(command.plan()))
                .append("\n\nExecution results:\n")
                .append(executionSummary(command.executionResults()))
                .append("\n\nAnswer draft:\n")
                .append(safe(command.answerDraft()))
                .append("\n\nReturn one JSON object only. Schema:\n")
                .append("""
                        {
                          "passed": true,
                          "issues": [
                            {"taskId": "task-1", "level": "INFO or WARN or ERROR", "message": "issue detail"}
                          ],
                          "retryTasks": ["task-1"],
                          "summary": "short review summary",
                          "replanRequired": false,
                          "replanInstruction": "only when missing information requires new tasks"
                        }
                        """)
                .append("\nRules:\n")
                .append("- Review only the answer draft and execution results.\n")
                .append("- Do not create a new plan or execute tasks.\n")
                .append("- Do not call tools.\n")
                .append("- retryTasks can only contain existing task ids.\n");
        prompt.append("- Set replanRequired=true only when the answer needs information that cannot be obtained by retrying existing tasks.\n")
                .append("- When replanRequired=true, leave retryTasks empty and provide a concrete replanInstruction.\n");
        if (previousFailure != null && !previousFailure.isBlank()) {
            prompt.append("\nPrevious review was invalid: ").append(truncateFailure(previousFailure))
                    .append("\nCorrect it and return valid JSON only.\n");
        }
        return List.of(
                new ModelMessage("system", "You are the Reviewer in an Agent Team. Return validated JSON only."),
                new ModelMessage("user", prompt.toString())
        );
    }

    private ReviewResultDTO fallbackReview(ReviewTeamCommand command, String lastFailure) {
        List<ReviewResultDTO.ReviewIssueDTO> issues = new ArrayList<>();
        issues.add(new ReviewResultDTO.ReviewIssueDTO(null, "WARN", "Reviewer fallback used: " + safe(lastFailure)));
        if (command.answerDraft() == null || command.answerDraft().isBlank()) {
            issues.add(new ReviewResultDTO.ReviewIssueDTO(null, "WARN", "answerDraft is blank"));
        }
        return new ReviewResultDTO(
                true,
                issues,
                List.of(),
                "Reviewer fallback accepted current answer with warnings."
        );
    }

    private String planSummary(TaskPlanDTO plan) {
        if (plan == null || plan.tasks() == null || plan.tasks().isEmpty()) {
            return "(none)";
        }
        return plan.tasks().stream()
                .map(this::taskSummary)
                .collect(Collectors.joining("\n"));
    }

    private String taskSummary(TeamTaskDTO task) {
        return "- " + safe(task.id()) + " [" + safe(task.taskType()) + "] "
                + safe(task.name()) + ": " + safe(task.description());
    }

    private String executionSummary(List<ExecutionResultDTO> results) {
        if (results == null || results.isEmpty()) {
            return "(none)";
        }
        return results.stream()
                .map(result -> "- " + safe(result.taskId()) + " [" + safe(result.status()) + "]: "
                        + safe(result.result()) + errorSuffix(result))
                .collect(Collectors.joining("\n"));
    }

    private String errorSuffix(ExecutionResultDTO result) {
        return result.errorMessage() == null || result.errorMessage().isBlank()
                ? ""
                : " error=" + result.errorMessage();
    }

    private String profilePrompt(ReviewTeamCommand command) {
        ProfileDTO profile = command.context().profile();
        if (profile == null || profile.promptExtra() == null || profile.promptExtra().isBlank()) {
            return "(none)";
        }
        return profile.promptExtra().strip();
    }

    private String extractJson(String content) {
        String trimmed = content.strip();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) {
            return trimmed;
        }
        return trimmed.substring(start, end + 1);
    }

    private void validate(ReviewTeamCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "review team command is required");
        }
        if (command.context() == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "agent context is required");
        }
        if (command.context().modelConfigId() == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "modelConfigId is required");
        }
        if (command.plan() == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "task plan is required");
        }
    }

    private String truncateFailure(String previousFailure) {
        if (previousFailure.length() <= FAILURE_PROMPT_MAX_LENGTH) {
            return previousFailure;
        }
        return previousFailure.substring(0, FAILURE_PROMPT_MAX_LENGTH) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
