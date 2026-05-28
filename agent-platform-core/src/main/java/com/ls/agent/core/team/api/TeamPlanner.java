package com.ls.agent.core.team.api;

import com.ls.agent.core.team.command.PlanTeamCommand;
import com.ls.agent.core.team.dto.TeamPlanResultDTO;

public interface TeamPlanner {

    TeamPlanResultDTO plan(PlanTeamCommand command);
}
