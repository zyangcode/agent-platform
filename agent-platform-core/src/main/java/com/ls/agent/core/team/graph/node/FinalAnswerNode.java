package com.ls.agent.core.team.graph.node;

import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.team.graph.TeamGraphRuntimeContext;
import com.ls.agent.core.team.graph.TeamGraphState;
import com.ls.agent.core.team.graph.TeamGraphSupport;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FinalAnswerNode implements NodeActionWithConfig<TeamGraphState> {

    private final TeamGraphSupport support;

    public FinalAnswerNode(TeamGraphSupport support) {
        this.support = support;
    }

    @Override
    public Map<String, Object> apply(TeamGraphState state, RunnableConfig config) {
        TeamGraphRuntimeContext runtimeContext = runtimeContext(config);
        AgentRunCommand command = state.command();
        String finalAnswer = support.buildFinalAnswer(state.answerDraft(), state.review());
        List<ModelInvokeResult> fallbackModelInvocations = new ArrayList<>(state.fallbackModelInvocations());
        if (support.shouldGenerateFinalAnswer(finalAnswer)) {
            TeamGraphSupport.FallbackModelAnswer fallback = support.fallbackModelAnswer(command, state, runtimeContext);
            if (fallback.modelInvocation() != null) {
                fallbackModelInvocations.add(fallback.modelInvocation());
            }
            if (!fallback.answer().isBlank()) {
                finalAnswer = fallback.answer();
            }
        }
        TeamGraphState usageState = new TeamGraphState(withFallbackInvocations(state, fallbackModelInvocations));
        ModelUsageDTO usage = support.totalUsage(usageState);
        Integer step = state.step();
        support.emitFinalAnswer(command, runtimeContext, step);

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TeamGraphState.FALLBACK_MODEL_INVOCATIONS, List.copyOf(fallbackModelInvocations));
        updates.put(TeamGraphState.FINAL_ANSWER, finalAnswer);
        updates.put(TeamGraphState.USAGE, usage);
        updates.put(TeamGraphState.STEP, step + 1);
        return updates;
    }

    private Map<String, Object> withFallbackInvocations(
            TeamGraphState state,
            List<ModelInvokeResult> fallbackModelInvocations
    ) {
        Map<String, Object> data = new LinkedHashMap<>(state.data());
        data.put(TeamGraphState.FALLBACK_MODEL_INVOCATIONS, List.copyOf(fallbackModelInvocations));
        return data;
    }

    private TeamGraphRuntimeContext runtimeContext(RunnableConfig config) {
        return config.metadata(TeamGraphRuntimeContext.METADATA_KEY)
                .map(TeamGraphRuntimeContext.class::cast)
                .orElseThrow(() -> new IllegalStateException("Team graph runtime context is required"));
    }
}
