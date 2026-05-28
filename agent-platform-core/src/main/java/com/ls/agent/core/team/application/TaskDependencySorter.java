package com.ls.agent.core.team.application;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.team.dto.TeamTaskDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TaskDependencySorter {

    public List<TeamTaskDTO> sort(List<TeamTaskDTO> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        Map<String, TeamTaskDTO> taskById = new HashMap<>();
        for (TeamTaskDTO task : tasks) {
            if (task == null || task.id() == null || task.id().isBlank()) {
                fail("task.id is required");
            }
            String taskId = task.id().trim();
            if (taskById.containsKey(taskId)) {
                fail("task.id must be unique");
            }
            taskById.put(taskId, task);
        }

        List<TeamTaskDTO> sorted = new ArrayList<>();
        Map<String, VisitState> states = new HashMap<>();
        for (TeamTaskDTO task : tasks) {
            visit(task.id().trim(), taskById, states, sorted);
        }
        return List.copyOf(sorted);
    }

    private void visit(
            String taskId,
            Map<String, TeamTaskDTO> taskById,
            Map<String, VisitState> states,
            List<TeamTaskDTO> sorted
    ) {
        VisitState state = states.get(taskId);
        if (VisitState.VISITING.equals(state)) {
            fail("dependsOn contains cycle");
        }
        if (VisitState.VISITED.equals(state)) {
            return;
        }
        TeamTaskDTO task = taskById.get(taskId);
        if (task == null) {
            fail("dependsOn references missing task " + taskId);
        }

        states.put(taskId, VisitState.VISITING);
        for (String dependency : dependsOn(task)) {
            String dependencyId = dependency == null ? "" : dependency.trim();
            if (dependencyId.isBlank() || !taskById.containsKey(dependencyId)) {
                fail("task " + taskId + " dependsOn references missing task " + dependency);
            }
            visit(dependencyId, taskById, states, sorted);
        }
        states.put(taskId, VisitState.VISITED);
        sorted.add(task);
    }

    private List<String> dependsOn(TeamTaskDTO task) {
        return task.dependsOn() == null ? List.of() : task.dependsOn();
    }

    private void fail(String message) {
        throw new BizException(ErrorCode.REQUEST_INVALID, message);
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}
