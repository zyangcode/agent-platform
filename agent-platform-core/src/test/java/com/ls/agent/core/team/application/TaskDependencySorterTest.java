package com.ls.agent.core.team.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.core.team.dto.TeamTaskDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskDependencySorterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TaskDependencySorter sorter = new TaskDependencySorter();

    @Test
    void sortsTasksByDependencies() {
        List<TeamTaskDTO> sorted = sorter.sort(List.of(
                task("task-3", List.of("task-2")),
                task("task-1", List.of()),
                task("task-2", List.of("task-1"))
        ));

        assertThat(sorted).extracting(TeamTaskDTO::id).containsExactly("task-1", "task-2", "task-3");
    }

    @Test
    void rejectsMissingDependency() {
        assertThatThrownBy(() -> sorter.sort(List.of(task("task-1", List.of("missing")))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("dependsOn references missing task missing");
    }

    @Test
    void rejectsCyclicDependency() {
        assertThatThrownBy(() -> sorter.sort(List.of(
                        task("task-1", List.of("task-2")),
                        task("task-2", List.of("task-1"))
                )))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("dependsOn contains cycle");
    }

    private TeamTaskDTO task(String id, List<String> dependsOn) {
        return new TeamTaskDTO(
                id,
                id,
                id + " description",
                "MODEL_TASK",
                null,
                objectMapper.createObjectNode(),
                dependsOn
        );
    }
}
