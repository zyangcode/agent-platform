package com.ls.agent.core.team.api;

import com.ls.agent.core.team.command.PlanTeamCommand;
import com.ls.agent.core.team.dto.TaskPlanDTO;

public interface TeamPlanner {

    TaskPlanDTO plan(PlanTeamCommand command);
}
