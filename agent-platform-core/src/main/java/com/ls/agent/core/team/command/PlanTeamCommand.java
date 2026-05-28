package com.ls.agent.core.team.command;

import com.ls.agent.core.agent.tool.AgentToolDTO;
import com.ls.agent.core.context.dto.AgentContextDTO;

import java.util.List;

public record PlanTeamCommand(
        String userInput,
        AgentContextDTO context,
        List<AgentToolDTO> availableTools
) {
    public PlanTeamCommand {
        availableTools = availableTools == null ? List.of() : List.copyOf(availableTools);
    }
}
