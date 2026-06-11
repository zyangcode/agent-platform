package com.ls.agent.core.team.graph.node;

import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.team.dto.ExecutionResultDTO;
import com.ls.agent.core.team.dto.TeamTaskDTO;
import com.ls.agent.core.team.dto.TeamTaskExecutionResultDTO;
import com.ls.agent.core.team.graph.TeamGraphRuntimeContext;
import com.ls.agent.core.team.graph.TeamGraphState;
import com.ls.agent.core.team.graph.TeamGraphSupport;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExecuteBatchNode implements NodeActionWithConfig<TeamGraphState> {

    private final TeamGraphSupport support;

    public ExecuteBatchNode(TeamGraphSupport support) {
        this.support = support;
    }

    @Override
    public Map<String, Object> apply(TeamGraphState state, RunnableConfig config) {
        TeamGraphRuntimeContext runtimeContext = runtimeContext(config);
        AgentRunCommand command = state.command();
        List<TeamTaskExecutionResultDTO> taskExecutionResults = new ArrayList<>(state.taskExecutionResults());
        List<ExecutionResultDTO> executionResults = new ArrayList<>(state.executionResults());
        int step = state.step();

        for (TeamTaskDTO task : state.scheduledTasks()) {
            TeamGraphState taskState = new TeamGraphState(withStepAndResults(state, step, executionResults));
            TeamTaskExecutionResultDTO taskResult = support.executeTask(command, taskState, task, runtimeContext);
            taskExecutionResults.add(taskResult);
            replaceResult(executionResults, taskResult.executionResult());
            step += emittedEventCount(task, taskResult);
        }

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TeamGraphState.TASK_EXECUTION_RESULTS, List.copyOf(taskExecutionResults));
        updates.put(TeamGraphState.EXECUTION_RESULTS, List.copyOf(executionResults));
        updates.put(TeamGraphState.STEP, step);
        return updates;
    }

    private Map<String, Object> withStepAndResults(TeamGraphState state, int step, List<ExecutionResultDTO> executionResults) {
        Map<String, Object> data = new LinkedHashMap<>(state.data());
        data.put(TeamGraphState.STEP, step);
        data.put(TeamGraphState.EXECUTION_RESULTS, List.copyOf(executionResults));
        return data;
    }

    private void replaceResult(List<ExecutionResultDTO> results, ExecutionResultDTO replacement) {
        for (int index = 0; index < results.size(); index++) {
            if (replacement.taskId().equals(results.get(index).taskId())) {
                results.set(index, replacement);
                return;
            }
        }
        results.add(replacement);
    }

    private int emittedEventCount(TeamTaskDTO task, TeamTaskExecutionResultDTO taskResult) {
        int count = 2;
        if ("TOOL_TASK".equals(task.taskType())) {
            count++;
        }
        count += taskResult.toolResults().size();
        return count;
    }

    private TeamGraphRuntimeContext runtimeContext(RunnableConfig config) {
        return config.metadata(TeamGraphRuntimeContext.METADATA_KEY)
                .map(TeamGraphRuntimeContext.class::cast)
                .orElseThrow(() -> new IllegalStateException("Team graph runtime context is required"));
    }
}
