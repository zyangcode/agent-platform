package com.ls.agent.core.team.graph;

import com.ls.agent.core.agent.command.AgentRunCommand;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

public final class TeamGraphState extends AgentState {

    public static final String COMMAND = "command";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String RUN_SPAN_ID = "runSpanId";
    public static final String ROUTE = "route";

    public TeamGraphState(Map<String, Object> initData) {
        super(initData);
    }

    public static TeamGraphState initial(AgentRunCommand command, Long runSpanId) {
        return new TeamGraphState(Map.of(
                COMMAND, command,
                CONVERSATION_ID, command.conversationId(),
                RUN_SPAN_ID, runSpanId,
                ROUTE, TeamGraphRoute.FINAL
        ));
    }

    public AgentRunCommand command() {
        return value(COMMAND).map(AgentRunCommand.class::cast).orElse(null);
    }

    public Long conversationId() {
        return value(CONVERSATION_ID).map(Long.class::cast).orElse(null);
    }

    public Long runSpanId() {
        return value(RUN_SPAN_ID).map(Long.class::cast).orElse(null);
    }

    public TeamGraphRoute route() {
        return value(ROUTE).map(TeamGraphRoute.class::cast).orElse(TeamGraphRoute.FINAL);
    }
}
