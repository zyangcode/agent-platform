package com.ls.agent.core.team.dto;

import java.util.List;

public record TaskPlanDTO(
        String goal,
        List<TeamTaskDTO> tasks
) {
    public TaskPlanDTO {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
