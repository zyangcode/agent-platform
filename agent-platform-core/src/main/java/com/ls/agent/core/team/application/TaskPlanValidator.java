package com.ls.agent.core.team.application;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.team.dto.TaskPlanDTO;
import com.ls.agent.core.team.dto.TeamTaskDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TaskPlanValidator {

    private static final String TASK_TYPE_TOOL = "TOOL_TASK";
    private static final String TASK_TYPE_MODEL = "MODEL_TASK";

    public void validate(TaskPlanDTO plan, Set<String> availableToolNames) {
        if (plan == null) {
            fail("task plan is required");
        }
        if (isBlank(plan.goal())) {
            fail("goal is required");
        }
        List<TeamTaskDTO> tasks = plan.tasks();
        if (tasks == null || tasks.isEmpty()) {
            fail("tasks are required");
        }
        if (tasks.size() > TeamLimits.DEFAULT_MAX_TASKS) {
            fail("tasks exceed maxTasks");
        }

        Map<String, TeamTaskDTO> taskById = collectTasks(tasks);
        validateTasks(tasks, safeToolNames(availableToolNames));
        validateDependencies(tasks, taskById.keySet());
    }

    private Map<String, TeamTaskDTO> collectTasks(List<TeamTaskDTO> tasks) {
        Map<String, TeamTaskDTO> taskById = new HashMap<>();
        for (TeamTaskDTO task : tasks) {
            if (task == null) {
                fail("task is required");
            }
            if (isBlank(task.id())) {
                fail("task.id is required");
            }
            String taskId = task.id().trim();
            if (taskById.containsKey(taskId)) {
                fail("task.id must be unique");
            }
            taskById.put(taskId, task);
        }
        return taskById;
    }

    private void validateTasks(List<TeamTaskDTO> tasks, Set<String> availableToolNames) {
        for (TeamTaskDTO task : tasks) {
            if (isBlank(task.name())) {
                fail("task.name is required");
            }
            if (!TASK_TYPE_TOOL.equals(task.taskType()) && !TASK_TYPE_MODEL.equals(task.taskType())) {
                fail("task.taskType must be TOOL_TASK or MODEL_TASK");
            }
            if (TASK_TYPE_TOOL.equals(task.taskType())) {
                if (isBlank(task.suggestedTool())) {
                    fail("TOOL_TASK requires suggestedTool");
                }
                if (!availableToolNames.contains(task.suggestedTool().trim())) {
                    fail("suggestedTool is not available");
                }
            }
        }
    }

    private void validateDependencies(List<TeamTaskDTO> tasks, Set<String> taskIds) {
        for (TeamTaskDTO task : tasks) {
            for (String dependency : safeDependsOn(task)) {
                if (isBlank(dependency) || !taskIds.contains(dependency.trim())) {
                    fail("task " + task.id().trim() + " dependsOn references missing task " + dependency);
                }
            }
        }

        Map<String, VisitState> states = new HashMap<>();
        Map<String, TeamTaskDTO> taskById = new HashMap<>();
        for (TeamTaskDTO task : tasks) {
            taskById.put(task.id().trim(), task);
        }
        for (String taskId : taskIds) {
            detectCycle(taskId, taskById, states);
        }
    }

    private void detectCycle(String taskId, Map<String, TeamTaskDTO> taskById, Map<String, VisitState> states) {
        VisitState state = states.get(taskId);
        if (VisitState.VISITING.equals(state)) {
            fail("dependsOn contains cycle");
        }
        if (VisitState.VISITED.equals(state)) {
            return;
        }

        states.put(taskId, VisitState.VISITING);
        for (String dependency : safeDependsOn(taskById.get(taskId))) {
            detectCycle(dependency.trim(), taskById, states);
        }
        states.put(taskId, VisitState.VISITED);
    }

    private Set<String> safeToolNames(Set<String> availableToolNames) {
        return availableToolNames == null ? Set.of() : availableToolNames;
    }

    private List<String> safeDependsOn(TeamTaskDTO task) {
        return task.dependsOn() == null ? List.of() : task.dependsOn();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void fail(String message) {
        throw new BizException(ErrorCode.REQUEST_INVALID, message);
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}
