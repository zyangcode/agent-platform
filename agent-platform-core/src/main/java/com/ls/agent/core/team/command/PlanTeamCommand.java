package com.ls.agent.core.team.command;

import com.ls.agent.core.agent.tool.AgentToolDTO;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.team.dto.ExecutionResultDTO;
import com.ls.agent.core.team.dto.ReviewResultDTO;
import com.ls.agent.core.team.dto.TaskPlanDTO;

import java.util.List;

public record PlanTeamCommand(
        String userInput,
        AgentContextDTO context,
        List<AgentToolDTO> availableTools,
        TaskPlanDTO previousPlan,
        List<ExecutionResultDTO> previousResults,
        ReviewResultDTO previousReview
) {
    public PlanTeamCommand {
        availableTools = availableTools == null ? List.of() : List.copyOf(availableTools);
        previousResults = previousResults == null ? List.of() : List.copyOf(previousResults);
    }

    public PlanTeamCommand(
            String userInput,
            AgentContextDTO context,
            List<AgentToolDTO> availableTools
    ) {
        this(userInput, context, availableTools, null, List.of(), null);
    }
}
