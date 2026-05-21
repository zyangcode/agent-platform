package com.ls.agent.core.context.api;

import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.AgentContextDTO;

public interface AgentContextBuilder {

    AgentContextDTO build(BuildAgentContextCommand command);
}
