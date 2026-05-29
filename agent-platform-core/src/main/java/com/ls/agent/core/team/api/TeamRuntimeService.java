package com.ls.agent.core.team.api;

import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.dto.AgentRunResult;

public interface TeamRuntimeService {

    AgentRunResult run(AgentRunCommand command);

    AgentRunResult run(AgentRunCommand command, TeamEventSink eventSink);
}
