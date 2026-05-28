package com.ls.agent.core.agent.tool;

import com.ls.agent.core.context.dto.AgentContextDTO;

import java.util.List;

public interface AgentToolResolver {

    List<AgentToolDTO> resolve(AgentContextDTO context);
}
