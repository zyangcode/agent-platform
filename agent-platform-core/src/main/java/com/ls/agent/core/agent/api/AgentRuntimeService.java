package com.ls.agent.core.agent.api;

import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.dto.AgentRunResult;
import com.ls.agent.core.model.api.ModelStreamCallback;
import com.ls.agent.core.team.api.TeamEventSink;

public interface AgentRuntimeService {

    AgentRunResult run(AgentRunCommand command);

    default AgentRunResult run(AgentRunCommand command, TeamEventSink teamEventSink) {
        return run(command);
    }

    default AgentRunResult run(AgentRunCommand command, TeamEventSink teamEventSink, ModelStreamCallback streamCallback) {
        return run(command, teamEventSink);
    }
}
