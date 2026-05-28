package com.ls.agent.core.team.command;

import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.team.dto.ExecutionResultDTO;
import com.ls.agent.core.team.dto.TaskPlanDTO;

import java.util.List;

public record ReviewTeamCommand(
        String userInput,
        TaskPlanDTO plan,
        List<ExecutionResultDTO> executionResults,
        String answerDraft,
        AgentContextDTO context
) {
    public ReviewTeamCommand {
        executionResults = executionResults == null ? List.of() : List.copyOf(executionResults);
    }
}
