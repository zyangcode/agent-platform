package com.ls.agent.core.team.graph;

import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.tool.AgentToolDTO;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.team.dto.ExecutionResultDTO;
import com.ls.agent.core.team.dto.ReviewResultDTO;
import com.ls.agent.core.team.dto.TaskPlanDTO;
import com.ls.agent.core.team.dto.TeamPlanResultDTO;
import com.ls.agent.core.team.dto.TeamTaskDTO;
import org.bsc.langgraph4j.state.AgentState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TeamGraphState extends AgentState {

    public static final String COMMAND = "command";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String RUN_SPAN_ID = "runSpanId";
    public static final String STEP = "step";
    public static final String CONTEXT = "context";
    public static final String AVAILABLE_TOOLS = "availableTools";
    public static final String PLAN = "plan";
    public static final String PREVIOUS_PLAN = "previousPlan";
    public static final String PLAN_RESULTS = "planResults";
    public static final String EXECUTION_RESULTS = "executionResults";
    public static final String REVIEW = "review";
    public static final String SCHEDULED_TASKS = "scheduledTasks";
    public static final String FALLBACK_MODEL_INVOCATIONS = "fallbackModelInvocations";
    public static final String ROUTE = "route";

    public TeamGraphState(Map<String, Object> initData) {
        super(initData);
    }

    public static TeamGraphState initial(AgentRunCommand command, Long runSpanId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(COMMAND, command);
        data.put(CONVERSATION_ID, command.conversationId());
        data.put(RUN_SPAN_ID, runSpanId);
        data.put(STEP, 1);
        data.put(AVAILABLE_TOOLS, List.of());
        data.put(PLAN_RESULTS, List.of());
        data.put(EXECUTION_RESULTS, List.of());
        data.put(SCHEDULED_TASKS, List.of());
        data.put(FALLBACK_MODEL_INVOCATIONS, List.of());
        data.put(ROUTE, TeamGraphRoute.FINAL);
        return new TeamGraphState(data);
    }

    public AgentRunCommand command() {
        return value(COMMAND).map(AgentRunCommand.class::cast).orElse(null);
    }

    public Long conversationId() {
        return value(CONVERSATION_ID).map(Long.class::cast).orElse(null);
    }

    public Long runSpanId() {
        return value(RUN_SPAN_ID).map(Long.class::cast).orElse(null);
    }

    public Integer step() {
        return value(STEP).map(Integer.class::cast).orElse(1);
    }

    public AgentContextDTO context() {
        return value(CONTEXT).map(AgentContextDTO.class::cast).orElse(null);
    }

    public List<AgentToolDTO> availableTools() {
        return value(AVAILABLE_TOOLS).map(TeamGraphState::<AgentToolDTO>castList).orElse(List.of());
    }

    public TaskPlanDTO plan() {
        return value(PLAN).map(TaskPlanDTO.class::cast).orElse(null);
    }

    public TaskPlanDTO previousPlan() {
        return value(PREVIOUS_PLAN).map(TaskPlanDTO.class::cast).orElse(null);
    }

    public List<TeamPlanResultDTO> planResults() {
        return value(PLAN_RESULTS).map(TeamGraphState::<TeamPlanResultDTO>castList).orElse(List.of());
    }

    public List<ExecutionResultDTO> executionResults() {
        return value(EXECUTION_RESULTS).map(TeamGraphState::<ExecutionResultDTO>castList).orElse(List.of());
    }

    public ReviewResultDTO review() {
        return value(REVIEW).map(ReviewResultDTO.class::cast).orElse(null);
    }

    public List<TeamTaskDTO> scheduledTasks() {
        return value(SCHEDULED_TASKS).map(TeamGraphState::<TeamTaskDTO>castList).orElse(List.of());
    }

    public List<ModelInvokeResult> fallbackModelInvocations() {
        return value(FALLBACK_MODEL_INVOCATIONS).map(TeamGraphState::<ModelInvokeResult>castList).orElse(List.of());
    }

    public TeamGraphRoute route() {
        return value(ROUTE).map(TeamGraphRoute.class::cast).orElse(TeamGraphRoute.FINAL);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> castList(Object value) {
        return value == null ? List.of() : (List<T>) value;
    }
}
