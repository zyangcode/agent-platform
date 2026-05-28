package com.ls.agent.core.team.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
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
    void rejectsMissingDependency() {
        TaskPlanDTO plan = new TaskPlanDTO(
                "Goal",
                List.of(task("task-1", "Depends", "MODEL_TASK", null, List.of("missing-task")))
        );

        assertValidationFailure(plan, Set.of(), "dependsOn references missing task");
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

    private TeamTaskDTO task(
            String id,
            String name,
            String taskType,
            String suggestedTool,
            List<String> dependsOn
    ) {
        return new TeamTaskDTO(
                id,
                name,
                name + " description",
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
