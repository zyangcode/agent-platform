package com.ls.agent.core.team.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.tool.AgentToolDTO;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.team.api.TeamPlanner;
import com.ls.agent.core.team.command.PlanTeamCommand;
import com.ls.agent.core.team.dto.ExecutionResultDTO;
import com.ls.agent.core.team.dto.ReviewResultDTO;
import com.ls.agent.core.team.dto.TaskPlanDTO;
import com.ls.agent.core.team.dto.TeamPlanResultDTO;
import com.ls.agent.core.team.dto.TeamTaskDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DefaultTeamPlanner implements TeamPlanner {

    private static final BigDecimal PLANNER_TEMPERATURE = BigDecimal.valueOf(0.2);
    private static final int MAX_MODEL_ATTEMPTS = 2;
    private static final int FAILURE_PROMPT_MAX_LENGTH = 200;

    private final ModelInvokeService modelInvokeService;
    private final TaskPlanValidator taskPlanValidator;
    private final ObjectMapper objectMapper;

    public DefaultTeamPlanner(
            ModelInvokeService modelInvokeService,
            TaskPlanValidator taskPlanValidator,
            ObjectMapper objectMapper
    ) {
        this.modelInvokeService = modelInvokeService;
        this.taskPlanValidator = taskPlanValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public TeamPlanResultDTO plan(PlanTeamCommand command) {
        validate(command);
        String lastFailure = null;
        List<ModelInvokeResult> modelInvocations = new ArrayList<>();
        for (int attempt = 1; attempt <= MAX_MODEL_ATTEMPTS; attempt++) {
            try {
                ModelInvokeResult modelResult = invokeModel(command, lastFailure);
                if (modelResult != null) {
                    modelInvocations.add(modelResult);
                }
                TaskPlanDTO plan = parsePlan(modelResult);
                taskPlanValidator.validate(plan, toolNames(command.availableTools()));
                return new TeamPlanResultDTO(plan, modelInvocations);
            } catch (Exception ex) {
                lastFailure = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            }
        }
        TaskPlanDTO fallback = fallbackPlan(command.userInput());
        taskPlanValidator.validate(fallback, toolNames(command.availableTools()));
        return new TeamPlanResultDTO(fallback, modelInvocations);
    }

    private ModelInvokeResult invokeModel(PlanTeamCommand command, String previousFailure) {
        return modelInvokeService.invoke(new ModelInvokeCommand(
                command.context().modelConfigId(),
                messages(command, previousFailure),
                PLANNER_TEMPERATURE,
                false
        ));
    }

    private TaskPlanDTO parsePlan(ModelInvokeResult result) {
        String content = result == null ? null : result.assistantMessage();
        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "planner response is empty");
        }
        try {
            return objectMapper.readValue(extractJson(content), TaskPlanDTO.class);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "planner response is not valid TaskPlan JSON");
        }
    }

    private List<ModelMessage> messages(PlanTeamCommand command, String previousFailure) {
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("User request:\n")
                .append(command.userInput().strip())
                .append("\n\nProfile prompt:\n")
                .append(profilePrompt(command))
                .append("\n\nAvailable tools:\n")
                .append(formatTools(command.availableTools()))
                .append("\n\nReturn one JSON object only. Schema:\n")
                .append("""
                        {
                          "goal": "short goal",
                          "tasks": [
                            {
                              "id": "task-1",
                              "name": "task name",
                              "description": "clear task instruction",
                              "taskType": "TOOL_TASK or MODEL_TASK",
                              "suggestedTool": "tool name for TOOL_TASK, null for MODEL_TASK",
                              "arguments": {},
                              "dependsOn": []
                            }
                          ]
                        }
                        """)
                .append("\nRules:\n")
                .append("- Create 1 to ").append(TeamLimits.DEFAULT_MAX_TASKS).append(" tasks.\n")
                .append("- Planner only creates a plan. It must not call tools or answer the user.\n")
                .append("- TOOL_TASK requires suggestedTool from the available tools list.\n")
                .append("- MODEL_TASK must use suggestedTool=null.\n")
                .append("- Every task must have a non-empty description.\n");
        if (isReplan(command)) {
            userPrompt.append("\nRe-plan mode:\n")
                    .append("- The Reviewer found missing information and requested a new plan.\n")
                    .append("- Return the full updated TaskPlan, including previous tasks and any new tasks.\n")
                    .append("- Keep existing completed task ids unchanged; do not rename or remove completed tasks.\n")
                    .append("- Add only the tasks needed to satisfy the review instruction.\n")
                    .append("\nPrevious plan:\n")
                    .append(planSummary(command.previousPlan()))
                    .append("\n\nPrevious execution results:\n")
                    .append(executionSummary(command.previousResults()))
                    .append("\n\nPrevious review:\n")
                    .append(reviewSummary(command.previousReview()))
                    .append("\n");
        }
        if (previousFailure != null && !previousFailure.isBlank()) {
            userPrompt.append("\nPrevious plan was invalid: ").append(truncateFailure(previousFailure))
                    .append("\nCorrect it and return valid JSON only.\n");
        }

        return List.of(
                new ModelMessage("system", "You are the Planner in an Agent Team. Split work into validated JSON tasks only."),
                new ModelMessage("user", userPrompt.toString())
        );
    }

    private String profilePrompt(PlanTeamCommand command) {
        ProfileDTO profile = command.context().profile();
        if (profile == null || profile.promptExtra() == null || profile.promptExtra().isBlank()) {
            return "(none)";
        }
        return profile.promptExtra().strip();
    }

    private String formatTools(List<AgentToolDTO> tools) {
        if (tools == null || tools.isEmpty()) {
            return "(none)";
        }
        return tools.stream()
                .map(tool -> "[" + tool.sourceType().name() + "] " + tool.name() + " - " + safe(tool.description()))
                .collect(Collectors.joining("\n"));
    }

    private boolean isReplan(PlanTeamCommand command) {
        return command.previousPlan() != null || command.previousReview() != null || !command.previousResults().isEmpty();
    }

    private String planSummary(TaskPlanDTO plan) {
        if (plan == null || plan.tasks().isEmpty()) {
            return "(none)";
        }
        return plan.tasks().stream()
                .map(task -> "- " + safe(task.id()) + " [" + safe(task.taskType()) + "] "
                        + safe(task.name()) + ": " + safe(task.description()))
                .collect(Collectors.joining("\n"));
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

    private String reviewSummary(ReviewResultDTO review) {
        if (review == null) {
            return "(none)";
        }
        String issues = review.issues().isEmpty()
                ? "(none)"
                : review.issues().stream()
                .map(issue -> "- " + safe(issue.taskId()) + " [" + safe(issue.level()) + "]: " + safe(issue.message()))
                .collect(Collectors.joining("\n"));
        return "passed=" + review.passed()
                + "\nsummary=" + safe(review.summary())
                + "\nreplanRequired=" + review.replanRequired()
                + "\nreplanInstruction=" + safe(review.replanInstruction())
                + "\nissues:\n" + issues;
    }

    private String errorSuffix(ExecutionResultDTO result) {
        return result.errorMessage() == null || result.errorMessage().isBlank()
                ? ""
                : " error=" + result.errorMessage();
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

    private Set<String> toolNames(List<AgentToolDTO> tools) {
        if (tools == null) {
            return Set.of();
        }
        return tools.stream()
                .map(AgentToolDTO::name)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }

    private TaskPlanDTO fallbackPlan(String userInput) {
        String goal = userInput.strip();
        return new TaskPlanDTO(
                goal,
                List.of(new TeamTaskDTO(
                        "task-1",
                        "Answer user request",
                        "Answer the original user request directly: " + goal,
                        "MODEL_TASK",
                        null,
                        objectMapper.createObjectNode(),
                        List.of()
                ))
        );
    }

    private void validate(PlanTeamCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "plan team command is required");
        }
        if (command.context() == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "agent context is required");
        }
        if (command.context().modelConfigId() == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "modelConfigId is required");
        }
        if (command.userInput() == null || command.userInput().isBlank()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "userInput is required");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String truncateFailure(String previousFailure) {
        if (previousFailure.length() <= FAILURE_PROMPT_MAX_LENGTH) {
            return previousFailure;
        }
        return previousFailure.substring(0, FAILURE_PROMPT_MAX_LENGTH) + "...";
    }
}
