package com.ls.agent.core.team.command;

import com.ls.agent.core.agent.tool.AgentToolDTO;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.team.dto.ExecutionResultDTO;
import com.ls.agent.core.team.dto.TeamTaskDTO;

import java.util.List;

public record ExecuteTeamTaskCommand(
        Long tenantId,
        Long userId,
        String userInput,
        TeamTaskDTO task,
        AgentContextDTO context,
        List<AgentToolDTO> availableTools,
        List<ExecutionResultDTO> previousResults
) {
    public ExecuteTeamTaskCommand {
        availableTools = availableTools == null ? List.of() : List.copyOf(availableTools);
        previousResults = previousResults == null ? List.of() : List.copyOf(previousResults);
    }
}
