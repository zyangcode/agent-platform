package com.ls.agent.core.team.api;

import com.ls.agent.core.team.command.ExecuteTeamTaskCommand;
import com.ls.agent.core.team.dto.TeamTaskExecutionResultDTO;

public interface TeamExecutor {

    TeamTaskExecutionResultDTO execute(ExecuteTeamTaskCommand command);
}
