package com.ls.agent.core.team.graph.node;

import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.team.dto.TeamPlanResultDTO;
import com.ls.agent.core.team.graph.TeamGraphRuntimeContext;
import com.ls.agent.core.team.graph.TeamGraphState;
import com.ls.agent.core.team.graph.TeamGraphSupport;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlanNode implements NodeActionWithConfig<TeamGraphState> {

    private final TeamGraphSupport support;

    public PlanNode(TeamGraphSupport support) {
        this.support = support;
    }

    @Override
    public Map<String, Object> apply(TeamGraphState state, RunnableConfig config) {
        AgentRunCommand command = state.command();
        TeamGraphRuntimeContext runtimeContext = runtimeContext(config);
        TeamPlanResultDTO planResult = support.plan(command, state, runtimeContext.runSpanId());
        List<TeamPlanResultDTO> planResults = new ArrayList<>(state.planResults());
        planResults.add(planResult);
        Integer step = state.step();
        support.emitPlan(command, runtimeContext, step, planResult.plan());

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TeamGraphState.PLAN, planResult.plan());
        updates.put(TeamGraphState.PLAN_RESULTS, List.copyOf(planResults));
        updates.put(TeamGraphState.STEP, step + 1);
        return updates;
    }

    private TeamGraphRuntimeContext runtimeContext(RunnableConfig config) {
        return config.metadata(TeamGraphRuntimeContext.METADATA_KEY)
                .map(TeamGraphRuntimeContext.class::cast)
                .orElseThrow(() -> new IllegalStateException("Team graph runtime context is required"));
    }
}
