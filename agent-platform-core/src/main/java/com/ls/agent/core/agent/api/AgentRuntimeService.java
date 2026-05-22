package com.ls.agent.core.agent.api;

import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.dto.AgentRunResult;

public interface AgentRuntimeService {

    AgentRunResult run(AgentRunCommand command);
}
