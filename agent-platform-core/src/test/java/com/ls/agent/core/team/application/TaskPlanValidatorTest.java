package com.ls.agent.core.team.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.team.dto.ExecutionResultDTO;
import com.ls.agent.core.team.dto.ReviewResultDTO;
import com.ls.agent.core.team.dto.TaskPlanDTO;
import com.ls.agent.core.team.dto.TeamTaskDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskPlanValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TaskPlanValidator validator = new TaskPlanValidator();

    @Test
    void rejectsNullPlan() {
        assertValidationFailure(null, Set.of(), "task plan is required");
    }

    @Test
    void acceptsValidToolAndModelTasks() {
        TaskPlanDTO plan = new TaskPlanDTO(
                "Plan a team activity",
                List.of(
                        task("task-1", "Check weather", "TOOL_TASK", "weather", List.of()),
                        task("task-2", "Summarize options", "MODEL_TASK", null, List.of("task-1"))
                )
        );

        assertThatCode(() -> validator.validate(plan, Set.of("weather")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBlankGoal() {
        TaskPlanDTO plan = new TaskPlanDTO(" ", List.of(task("task-1", "Answer", "MODEL_TASK", null, List.of())));

        assertValidationFailure(plan, Set.of(), "goal is required");
    }

    @Test
    void rejectsEmptyTasks() {
        TaskPlanDTO plan = new TaskPlanDTO("Goal", List.of());

        assertValidationFailure(plan, Set.of(), "tasks are required");
    }

    @Test
    void rejectsTooManyTasks() {
        List<TeamTaskDTO> tasks = new ArrayList<>();
        for (int index = 1; index <= TeamLimits.DEFAULT_MAX_TASKS + 1; index++) {
            tasks.add(task("task-" + index, "Task " + index, "MODEL_TASK", null, List.of()));
        }

        assertValidationFailure(new TaskPlanDTO("Goal", tasks), Set.of(), "tasks exceed maxTasks");
    }

    @Test
    void rejectsDuplicateTaskId() {
        TaskPlanDTO plan = new TaskPlanDTO(
                "Goal",
                List.of(
                        task("task-1", "First", "MODEL_TASK", null, List.of()),
                        task("task-1", "Second", "MODEL_TASK", null, List.of())
                )
        );

        assertValidationFailure(plan, Set.of(), "task.id must be unique");
    }

    @Test
    void rejectsInvalidTaskType() {
        TaskPlanDTO plan = new TaskPlanDTO("Goal", List.of(task("task-1", "Bad", "UNKNOWN", null, List.of())));

        assertValidationFailure(plan, Set.of(), "task.taskType must be TOOL_TASK or MODEL_TASK");
    }

    @Test
    void rejectsBlankTaskDescription() {
        TaskPlanDTO plan = new TaskPlanDTO(
                "Goal",
                List.of(task("task-1", "Answer", " ", "MODEL_TASK", null, List.of()))
        );

        assertValidationFailure(plan, Set.of(), "task.description is required");
    }

    @Test
    void rejectsToolTaskWithoutSuggestedTool() {
        TaskPlanDTO plan = new TaskPlanDTO("Goal", List.of(task("task-1", "Tool task", "TOOL_TASK", " ", List.of())));

        assertValidationFailure(plan, Set.of(), "TOOL_TASK requires suggestedTool");
    }

    @Test
    void rejectsSuggestedToolNotAvailable() {
        TaskPlanDTO plan = new TaskPlanDTO("Goal", List.of(task("task-1", "Search", "TOOL_TASK", "search", List.of())));

        assertValidationFailure(plan, Set.of("weather"), "suggestedTool is not available");
    }

    @Test
    void rejectsModelTaskWithSuggestedTool() {
        TaskPlanDTO plan = new TaskPlanDTO(
                "Goal",
                List.of(task("task-1", "Summarize", "MODEL_TASK", "weather", List.of()))
        );

        assertValidationFailure(plan, Set.of("weather"), "MODEL_TASK must not have suggestedTool");
    }

    @Test
    void rejectsMissingDependency() {
        TaskPlanDTO plan = new TaskPlanDTO(
                "Goal",
                List.of(task("task-1", "Depends", "MODEL_TASK", null, List.of("missing-task")))
        );

        assertValidationFailure(plan, Set.of(), "task task-1 dependsOn references missing task missing-task");
    }

    @Test
    void rejectsCyclicDependency() {
        TaskPlanDTO plan = new TaskPlanDTO(
                "Goal",
                List.of(
                        task("task-1", "First", "MODEL_TASK", null, List.of("task-2")),
                        task("task-2", "Second", "MODEL_TASK", null, List.of("task-1"))
                )
        );

        assertValidationFailure(plan, Set.of(), "dependsOn contains cycle");
    }

    @Test
    void taskPlanCopiesTaskListDefensively() {
        List<TeamTaskDTO> tasks = new ArrayList<>();
        tasks.add(task("task-1", "First", "MODEL_TASK", null, List.of()));

        TaskPlanDTO plan = new TaskPlanDTO("Goal", tasks);
        tasks.clear();

        org.assertj.core.api.Assertions.assertThat(plan.tasks()).hasSize(1);
    }

    @Test
    void teamTaskCopiesDependsOnDefensively() {
        List<String> dependsOn = new ArrayList<>();
        dependsOn.add("task-1");

        TeamTaskDTO task = task("task-2", "Second", "MODEL_TASK", null, dependsOn);
        dependsOn.clear();

        org.assertj.core.api.Assertions.assertThat(task.dependsOn()).containsExactly("task-1");
    }

    @Test
    void executionResultCopiesUsedToolsDefensively() {
        List<String> usedTools = new ArrayList<>();
        usedTools.add("weather");

        ExecutionResultDTO result = new ExecutionResultDTO("task-1", "TOOL_TASK", "SUCCESS", "ok", usedTools, null);
        usedTools.clear();

        org.assertj.core.api.Assertions.assertThat(result.usedTools()).containsExactly("weather");
    }

    @Test
    void reviewResultCopiesListsDefensively() {
        List<ReviewResultDTO.ReviewIssueDTO> issues = new ArrayList<>();
        issues.add(new ReviewResultDTO.ReviewIssueDTO("task-1", "WARN", "Need detail"));
        List<String> retryTasks = new ArrayList<>();
        retryTasks.add("task-1");

        ReviewResultDTO result = new ReviewResultDTO(false, issues, retryTasks, "Needs retry");
        issues.clear();
        retryTasks.clear();

        org.assertj.core.api.Assertions.assertThat(result.issues()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(result.retryTasks()).containsExactly("task-1");
    }

    private TeamTaskDTO task(
            String id,
            String name,
            String taskType,
            String suggestedTool,
            List<String> dependsOn
    ) {
        return task(id, name, name + " description", taskType, suggestedTool, dependsOn);
    }

    private TeamTaskDTO task(
            String id,
            String name,
            String description,
            String taskType,
            String suggestedTool,
            List<String> dependsOn
    ) {
        return new TeamTaskDTO(
                id,
                name,
                description,
                taskType,
                suggestedTool,
                objectMapper.createObjectNode(),
                dependsOn
        );
    }

    private void assertValidationFailure(TaskPlanDTO plan, Set<String> availableTools, String message) {
        assertThatThrownBy(() -> validator.validate(plan, availableTools))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException bizException = (BizException) error;
                    org.assertj.core.api.Assertions.assertThat(bizException.getCode())
                            .isEqualTo(ErrorCode.REQUEST_INVALID.getCode());
                    org.assertj.core.api.Assertions.assertThat(bizException.getMessage()).contains(message);
                });
    }
}
