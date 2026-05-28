package com.ls.agent.core.agent.tool;

public interface AgentToolDispatcher {

    AgentToolDispatchResult dispatch(AgentToolDispatchCommand command);
}
