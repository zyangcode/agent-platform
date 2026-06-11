package com.ls.agent.core.team.graph.node;

import com.ls.agent.core.team.graph.TeamGraphState;
import com.ls.agent.core.team.graph.TeamGraphSupport;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;

public class ScheduleNode implements NodeAction<TeamGraphState> {

    private final TeamGraphSupport support;

    public ScheduleNode(TeamGraphSupport support) {
        this.support = support;
    }

    @Override
    public Map<String, Object> apply(TeamGraphState state) {
        return Map.of(TeamGraphState.SCHEDULED_TASKS, support.schedule(state.plan()));
    }
}
