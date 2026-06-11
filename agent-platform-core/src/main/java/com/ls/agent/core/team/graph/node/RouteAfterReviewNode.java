package com.ls.agent.core.team.graph.node;

import com.ls.agent.core.team.dto.ReviewResultDTO;
import com.ls.agent.core.team.graph.TeamGraphRoute;
import com.ls.agent.core.team.graph.TeamGraphRuntimeContext;
import com.ls.agent.core.team.graph.TeamGraphState;
import com.ls.agent.core.team.graph.TeamGraphSupport;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.LinkedHashMap;
import java.util.Map;

public class RouteAfterReviewNode implements NodeActionWithConfig<TeamGraphState> {

    private final TeamGraphSupport support;

    public RouteAfterReviewNode(TeamGraphSupport support) {
        this.support = support;
    }

    @Override
    public Map<String, Object> apply(TeamGraphState state, RunnableConfig config) {
        TeamGraphRuntimeContext runtimeContext = runtimeContext(config);
        ReviewResultDTO review = state.review();
        Map<String, Object> updates = new LinkedHashMap<>();
        if (needsRetry(review)) {
            if (!tryConsumeRetry(runtimeContext)) {
                updates.put(TeamGraphState.ROUTE, TeamGraphRoute.FINAL);
                return updates;
            }
            String retryTaskId = review.retryTasks().get(0);
            support.emitRetry(state.command(), runtimeContext, state.step(), retryTaskId, "Reviewer requested task retry");
            updates.put(TeamGraphState.ROUTE, TeamGraphRoute.RETRY);
            updates.put(TeamGraphState.RETRY_TASK_ID, retryTaskId);
            updates.put(TeamGraphState.SCHEDULED_TASKS, java.util.List.of(support.findTask(state.plan(), retryTaskId)));
            updates.put(TeamGraphState.STEP, state.step() + 1);
            return updates;
        }
        if (needsReplan(review)) {
            if (!tryConsumeRetry(runtimeContext)) {
                updates.put(TeamGraphState.ROUTE, TeamGraphRoute.FINAL);
                return updates;
            }
            support.emitRetry(state.command(), runtimeContext, state.step(), null, "Reviewer requested replan");
            updates.put(TeamGraphState.PREVIOUS_PLAN, state.plan());
            updates.put(TeamGraphState.SCHEDULED_TASKS, java.util.List.of());
            updates.put(TeamGraphState.ROUTE, TeamGraphRoute.REPLAN);
            updates.put(TeamGraphState.STEP, state.step() + 1);
            return updates;
        }
        updates.put(TeamGraphState.ROUTE, TeamGraphRoute.FINAL);
        return updates;
    }

    private boolean needsRetry(ReviewResultDTO review) {
        return review != null && Boolean.FALSE.equals(review.passed()) && !review.retryTasks().isEmpty();
    }

    private boolean needsReplan(ReviewResultDTO review) {
        return review != null
                && Boolean.FALSE.equals(review.passed())
                && Boolean.TRUE.equals(review.replanRequired())
                && review.retryTasks().isEmpty();
    }

    private TeamGraphRuntimeContext runtimeContext(RunnableConfig config) {
        return config.metadata(TeamGraphRuntimeContext.METADATA_KEY)
                .map(TeamGraphRuntimeContext.class::cast)
                .orElseThrow(() -> new IllegalStateException("Team graph runtime context is required"));
    }

    private boolean tryConsumeRetry(TeamGraphRuntimeContext runtimeContext) {
        return runtimeContext.limiter() == null || runtimeContext.limiter().tryConsumeRetry();
    }
}
