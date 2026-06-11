package com.ls.agent.core.team.graph;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.LinkedHashMap;
import java.util.Map;

public class TeamGraphFactory {

    private final CompiledGraph<TeamGraphState> graph;

    public TeamGraphFactory() {
        this.graph = compileGraph();
    }

    public TeamGraphState invoke(TeamGraphState initialState, TeamGraphRuntimeContext runtimeContext) {
        try {
            Map<String, Object> graphInput = new LinkedHashMap<>(initialState.data());
            Object command = graphInput.remove(TeamGraphState.COMMAND);
            TeamGraphState graphResult = graph.invoke(graphInput)
                    .orElseThrow(() -> new IllegalStateException("Team graph completed without final state"));
            Map<String, Object> finalData = new LinkedHashMap<>(graphResult.data());
            if (command != null) {
                finalData.put(TeamGraphState.COMMAND, command);
            }
            return new TeamGraphState(finalData);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to invoke team graph", ex);
        }
    }

    private CompiledGraph<TeamGraphState> compileGraph() {
        try {
            NodeAction<TeamGraphState> finalAnswer = state -> Map.of(TeamGraphState.ROUTE, TeamGraphRoute.FINAL);
            return new StateGraph<>(TeamGraphState::new)
                    .addNode(TeamGraphNodeNames.FINAL_ANSWER, AsyncNodeAction.node_async(finalAnswer))
                    .addEdge(TeamGraphNodeNames.START, TeamGraphNodeNames.FINAL_ANSWER)
                    .addEdge(TeamGraphNodeNames.FINAL_ANSWER, TeamGraphNodeNames.END)
                    .compile();
        } catch (GraphStateException ex) {
            throw new IllegalStateException("Failed to compile team graph", ex);
        }
    }
}
