package com.ls.agent.core.team.graph.node;

import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.team.dto.TeamReviewResultDTO;
import com.ls.agent.core.team.graph.TeamGraphRuntimeContext;
import com.ls.agent.core.team.graph.TeamGraphState;
import com.ls.agent.core.team.graph.TeamGraphSupport;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReviewNode implements NodeActionWithConfig<TeamGraphState> {

    private final TeamGraphSupport support;

    public ReviewNode(TeamGraphSupport support) {
        this.support = support;
    }

    @Override
    public Map<String, Object> apply(TeamGraphState state, RunnableConfig config) {
        TeamGraphRuntimeContext runtimeContext = runtimeContext(config);
        AgentRunCommand command = state.command();
        String answerDraft = support.buildAnswerDraft(command, state);
        TeamGraphState reviewState = new TeamGraphState(withAnswerDraft(state, answerDraft));
        TeamReviewResultDTO reviewResult = support.review(command, reviewState, runtimeContext.runSpanId());
        if (runtimeContext.limiter() != null) {
            runtimeContext.limiter().consumeModelCalls(reviewResult.modelInvocations().size());
        }
        Integer step = state.step();
        support.emitReview(command, runtimeContext, step, answerDraft, reviewResult.reviewResult());
        List<TeamReviewResultDTO> reviewResults = new ArrayList<>(state.reviewResults());
        reviewResults.add(reviewResult);

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TeamGraphState.ANSWER_DRAFT, answerDraft);
        updates.put(TeamGraphState.REVIEW, reviewResult.reviewResult());
        updates.put(TeamGraphState.REVIEW_RESULTS, List.copyOf(reviewResults));
        updates.put(TeamGraphState.STEP, step + 1);
        return updates;
    }

    private Map<String, Object> withAnswerDraft(TeamGraphState state, String answerDraft) {
        Map<String, Object> data = new LinkedHashMap<>(state.data());
        data.put(TeamGraphState.ANSWER_DRAFT, answerDraft);
        return data;
    }

    private TeamGraphRuntimeContext runtimeContext(RunnableConfig config) {
        return config.metadata(TeamGraphRuntimeContext.METADATA_KEY)
                .map(TeamGraphRuntimeContext.class::cast)
                .orElseThrow(() -> new IllegalStateException("Team graph runtime context is required"));
    }
}
