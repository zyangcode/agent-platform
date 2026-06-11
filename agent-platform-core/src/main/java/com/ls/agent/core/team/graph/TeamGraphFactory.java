package com.ls.agent.core.team.graph;

import com.ls.agent.core.team.graph.node.BuildContextNode;
import com.ls.agent.core.team.graph.node.ExecuteBatchNode;
import com.ls.agent.core.team.graph.node.FinalAnswerNode;
import com.ls.agent.core.team.graph.node.PlanNode;
import com.ls.agent.core.team.graph.node.ReviewNode;
import com.ls.agent.core.team.graph.node.RouteAfterReviewNode;
import com.ls.agent.core.team.graph.node.ScheduleNode;
import com.ls.agent.core.team.graph.node.ValidatePlanNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.action.NodeActionWithConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TeamGraphFactory {

    private final CompiledGraph<TeamGraphState> graph;

    public TeamGraphFactory() {
        this.graph = compileGraph(null);
    }

    @Autowired
    public TeamGraphFactory(TeamGraphSupport support) {
        this.graph = compileGraph(support);
    }

    public TeamGraphState invoke(TeamGraphState initialState, TeamGraphRuntimeContext runtimeContext) {
        try {
            TeamGraphState graphResult = graph.invoke(
                            new LinkedHashMap<>(initialState.data()),
                            RunnableConfig.builder()
                                    .putMetadata(TeamGraphRuntimeContext.METADATA_KEY, runtimeContext)
                                    .build()
                    )
                    .orElseThrow(() -> new IllegalStateException("Team graph completed without final state"));
            return graphResult;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to invoke team graph: " + rootCauseMessage(ex), ex);
        }
    }

    private CompiledGraph<TeamGraphState> compileGraph(TeamGraphSupport support) {
        try {
            NodeAction<TeamGraphState> finalAnswer = state -> Map.of();
            StateGraph<TeamGraphState> stateGraph = new StateGraph<>(new TeamGraphStateSerializer());
            if (support == null) {
                return stateGraph
                        .addNode(TeamGraphNodeNames.FINAL_ANSWER, AsyncNodeAction.node_async(finalAnswer))
                        .addEdge(TeamGraphNodeNames.START, TeamGraphNodeNames.FINAL_ANSWER)
                        .addEdge(TeamGraphNodeNames.FINAL_ANSWER, TeamGraphNodeNames.END)
                        .compile();
            }
            return stateGraph
                    .addNode(TeamGraphNodeNames.BUILD_CONTEXT, nodeWithConfig(new BuildContextNode(support)))
                    .addNode(TeamGraphNodeNames.PLAN, nodeWithConfig(new PlanNode(support)))
                    .addNode(TeamGraphNodeNames.VALIDATE_PLAN, nodeWithConfig(new ValidatePlanNode(support)))
                    .addNode(TeamGraphNodeNames.SCHEDULE, AsyncNodeAction.node_async(new ScheduleNode(support)))
                    .addNode(TeamGraphNodeNames.EXECUTE_BATCH, nodeWithConfig(new ExecuteBatchNode(support)))
                    .addNode(TeamGraphNodeNames.REVIEW, nodeWithConfig(new ReviewNode(support)))
                    .addNode(TeamGraphNodeNames.ROUTE_AFTER_REVIEW, nodeWithConfig(new RouteAfterReviewNode(support)))
                    .addNode(TeamGraphNodeNames.FINAL_ANSWER, nodeWithConfig(new FinalAnswerNode(support)))
                    .addEdge(TeamGraphNodeNames.START, TeamGraphNodeNames.BUILD_CONTEXT)
                    .addEdge(TeamGraphNodeNames.BUILD_CONTEXT, TeamGraphNodeNames.PLAN)
                    .addEdge(TeamGraphNodeNames.PLAN, TeamGraphNodeNames.VALIDATE_PLAN)
                    .addEdge(TeamGraphNodeNames.VALIDATE_PLAN, TeamGraphNodeNames.SCHEDULE)
                    .addEdge(TeamGraphNodeNames.SCHEDULE, TeamGraphNodeNames.EXECUTE_BATCH)
                    .addEdge(TeamGraphNodeNames.EXECUTE_BATCH, TeamGraphNodeNames.REVIEW)
                    .addEdge(TeamGraphNodeNames.REVIEW, TeamGraphNodeNames.ROUTE_AFTER_REVIEW)
                    .addConditionalEdges(
                            TeamGraphNodeNames.ROUTE_AFTER_REVIEW,
                            routeAfterReview(),
                            Map.of(
                                    TeamGraphRoute.RETRY.name(), TeamGraphNodeNames.EXECUTE_BATCH,
                                    TeamGraphRoute.REPLAN.name(), TeamGraphNodeNames.PLAN,
                                    TeamGraphRoute.FINAL.name(), TeamGraphNodeNames.FINAL_ANSWER
                            )
                    )
                    .addEdge(TeamGraphNodeNames.FINAL_ANSWER, TeamGraphNodeNames.END)
                    .compile();
        } catch (GraphStateException ex) {
            throw new IllegalStateException("Failed to compile team graph", ex);
        }
    }

    private AsyncNodeActionWithConfig<TeamGraphState> nodeWithConfig(NodeActionWithConfig<TeamGraphState> node) {
        return AsyncNodeActionWithConfig.node_async(node);
    }

    private AsyncEdgeAction<TeamGraphState> routeAfterReview() {
        EdgeAction<TeamGraphState> route = state -> state.route().name();
        return AsyncEdgeAction.edge_async(route);
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        Throwable root = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                root = current;
            }
            current = current.getCause();
        }
        String message = root == null ? null : root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
