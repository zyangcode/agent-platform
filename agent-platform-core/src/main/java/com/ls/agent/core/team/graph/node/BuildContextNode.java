package com.ls.agent.core.team.graph.node;

import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.team.graph.TeamGraphRuntimeContext;
import com.ls.agent.core.team.graph.TeamGraphState;
import com.ls.agent.core.team.graph.TeamGraphSupport;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.Map;

public class BuildContextNode implements NodeActionWithConfig<TeamGraphState> {

    private final TeamGraphSupport support;

    public BuildContextNode(TeamGraphSupport support) {
        this.support = support;
    }

    @Override
    public Map<String, Object> apply(TeamGraphState state, RunnableConfig config) {
        AgentRunCommand command = state.command();
        TeamGraphRuntimeContext runtimeContext = runtimeContext(config);
        AgentContextDTO context = support.buildContext(command, state.conversationId(), runtimeContext.runSpanId());
        return Map.of(
                TeamGraphState.CONTEXT, context,
                TeamGraphState.AVAILABLE_TOOLS, support.resolveTools(context)
        );
    }

    private TeamGraphRuntimeContext runtimeContext(RunnableConfig config) {
        return config.metadata(TeamGraphRuntimeContext.METADATA_KEY)
                .map(TeamGraphRuntimeContext.class::cast)
                .orElseThrow(() -> new IllegalStateException("Team graph runtime context is required"));
    }
}
