package com.ls.agent.core.team.graph;

import com.ls.agent.core.team.graph.node.BuildContextNode;
import com.ls.agent.core.team.graph.node.PlanNode;
import com.ls.agent.core.team.graph.node.ScheduleNode;
import com.ls.agent.core.team.graph.node.ValidatePlanNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.LinkedHashMap;
import java.util.Map;

public class TeamGraphFactory {

    private final CompiledGraph<TeamGraphState> graph;

    public TeamGraphFactory() {
        this.graph = compileGraph(null);
    }

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
            throw new IllegalStateException("Failed to invoke team graph", ex);
        }
    }

    private CompiledGraph<TeamGraphState> compileGraph(TeamGraphSupport support) {
        try {
            NodeAction<TeamGraphState> finalAnswer = state -> Map.of(TeamGraphState.ROUTE, TeamGraphRoute.FINAL);
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
                    .addNode(TeamGraphNodeNames.FINAL_ANSWER, AsyncNodeAction.node_async(finalAnswer))
                    .addEdge(TeamGraphNodeNames.START, TeamGraphNodeNames.BUILD_CONTEXT)
                    .addEdge(TeamGraphNodeNames.BUILD_CONTEXT, TeamGraphNodeNames.PLAN)
                    .addEdge(TeamGraphNodeNames.PLAN, TeamGraphNodeNames.VALIDATE_PLAN)
                    .addEdge(TeamGraphNodeNames.VALIDATE_PLAN, TeamGraphNodeNames.SCHEDULE)
                    .addEdge(TeamGraphNodeNames.SCHEDULE, TeamGraphNodeNames.FINAL_ANSWER)
                    .addEdge(TeamGraphNodeNames.FINAL_ANSWER, TeamGraphNodeNames.END)
                    .compile();
        } catch (GraphStateException ex) {
            throw new IllegalStateException("Failed to compile team graph", ex);
        }
    }

    private AsyncNodeActionWithConfig<TeamGraphState> nodeWithConfig(NodeActionWithConfig<TeamGraphState> node) {
        return AsyncNodeActionWithConfig.node_async(node);
    }
}
