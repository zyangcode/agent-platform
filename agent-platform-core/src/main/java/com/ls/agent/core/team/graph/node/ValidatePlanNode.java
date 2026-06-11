package com.ls.agent.core.team.graph.node;

import com.ls.agent.core.team.graph.TeamGraphState;
import com.ls.agent.core.team.graph.TeamGraphRuntimeContext;
import com.ls.agent.core.team.graph.TeamGraphSupport;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.Map;

public class ValidatePlanNode implements NodeActionWithConfig<TeamGraphState> {

    private final TeamGraphSupport support;

    public ValidatePlanNode(TeamGraphSupport support) {
        this.support = support;
    }

    @Override
    public Map<String, Object> apply(TeamGraphState state, RunnableConfig config) {
        TeamGraphRuntimeContext runtimeContext = runtimeContext(config);
        if (runtimeContext.limiter() != null) {
            runtimeContext.limiter().checkTaskCount(state.plan().tasks().size());
        }
        support.validatePlan(state.plan(), state.availableTools());
        return Map.of();
    }

    private TeamGraphRuntimeContext runtimeContext(RunnableConfig config) {
        return config.metadata(TeamGraphRuntimeContext.METADATA_KEY)
                .map(TeamGraphRuntimeContext.class::cast)
                .orElseThrow(() -> new IllegalStateException("Team graph runtime context is required"));
    }
}
