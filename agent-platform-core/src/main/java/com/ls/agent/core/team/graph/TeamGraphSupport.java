package com.ls.agent.core.team.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.tool.AgentToolDTO;
import com.ls.agent.core.agent.tool.AgentToolDispatchResult;
import com.ls.agent.core.agent.tool.AgentToolResolver;
import com.ls.agent.core.context.api.AgentContextBuilder;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.quota.api.TokenUsageService;
import com.ls.agent.core.quota.command.RecordTokenUsageCommand;
import com.ls.agent.core.team.api.TeamPlanner;
import com.ls.agent.core.team.api.TeamExecutor;
import com.ls.agent.core.team.api.TeamReviewer;
import com.ls.agent.core.team.application.TaskDependencySorter;
import com.ls.agent.core.team.application.TeamAnswerDraftBuilder;
import com.ls.agent.core.team.application.TaskPlanValidator;
import com.ls.agent.core.team.command.ExecuteTeamTaskCommand;
import com.ls.agent.core.team.command.PlanTeamCommand;
import com.ls.agent.core.team.command.ReviewTeamCommand;
import com.ls.agent.core.team.dto.ExecutionResultDTO;
import com.ls.agent.core.team.dto.ReviewResultDTO;
import com.ls.agent.core.team.dto.TaskPlanDTO;
import com.ls.agent.core.team.dto.TeamPlanResultDTO;
import com.ls.agent.core.team.dto.TeamReviewResultDTO;
import com.ls.agent.core.team.dto.TeamRuntimeEventDTO;
import com.ls.agent.core.team.dto.TeamTaskDTO;
import com.ls.agent.core.team.dto.TeamTaskExecutionResultDTO;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.FinishTraceSpanCommand;
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceSpanDTO;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TeamGraphSupport {

    private final AgentContextBuilder contextBuilder;
    private final AgentToolResolver agentToolResolver;
    private final TeamPlanner planner;
    private final TeamExecutor executor;
    private final TeamReviewer reviewer;
    private final TeamAnswerDraftBuilder answerDraftBuilder;
    private final TaskPlanValidator taskPlanValidator;
    private final TaskDependencySorter taskDependencySorter;
    private final TraceService traceService;
    private final TokenUsageService tokenUsageService;
    private final ObjectMapper objectMapper;

    public TeamGraphSupport(
            AgentContextBuilder contextBuilder,
            AgentToolResolver agentToolResolver,
            TeamPlanner planner,
            TeamExecutor executor,
            TeamReviewer reviewer,
            TeamAnswerDraftBuilder answerDraftBuilder,
            TaskPlanValidator taskPlanValidator,
            TaskDependencySorter taskDependencySorter,
            TraceService traceService,
            TokenUsageService tokenUsageService,
            ObjectMapper objectMapper
    ) {
        this.contextBuilder = contextBuilder;
        this.agentToolResolver = agentToolResolver;
        this.planner = planner;
        this.executor = executor;
        this.reviewer = reviewer;
        this.answerDraftBuilder = answerDraftBuilder;
        this.taskPlanValidator = taskPlanValidator;
        this.taskDependencySorter = taskDependencySorter;
        this.traceService = traceService;
        this.tokenUsageService = tokenUsageService;
        this.objectMapper = objectMapper;
    }

    public AgentContextDTO buildContext(AgentRunCommand command, Long conversationId, Long parentSpanId) {
        TraceSpanDTO span = safeStartSpan(command.traceId(), parentSpanId, "context.build", "CONTEXT",
                objectMapper.createObjectNode()
                        .put("conversationId", conversationId)
                        .put("maxContextTokens", command.maxContextTokens() == null ? 0 : command.maxContextTokens()));
        try {
            AgentContextDTO context = contextBuilder.build(new BuildAgentContextCommand(
                    command.tenantId(),
                    command.userId(),
                    command.applicationId(),
                    command.profileId(),
                    conversationId,
                    command.userInput(),
                    command.maxContextTokens(),
                    command.selectedSkillIds(),
                    command.selectedMcpToolIds()
            ));
            safeFinishSpan(span, "SUCCESS", null, null);
            return context;
        } catch (Exception ex) {
            safeFinishSpan(span, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    public List<AgentToolDTO> resolveTools(AgentContextDTO context) {
        return agentToolResolver.resolve(context);
    }

    public TeamPlanResultDTO plan(AgentRunCommand command, TeamGraphState state, Long parentSpanId) {
        TraceSpanDTO span = safeStartSpan(command.traceId(), parentSpanId, "team.plan", "TEAM",
                objectMapper.createObjectNode());
        try {
            PlanTeamCommand planCommand = state.previousPlan() == null
                    ? new PlanTeamCommand(command.userInput(), state.context(), state.availableTools())
                    : new PlanTeamCommand(
                    command.userInput(),
                    state.context(),
                    state.availableTools(),
                    state.previousPlan(),
                    state.executionResults(),
                    state.review()
            );
            TeamPlanResultDTO result = planner.plan(planCommand);
            recordModelInvocations(command, result.modelInvocations(), spanId(span));
            safeFinishSpan(span, "SUCCESS", null, null);
            return result;
        } catch (Exception ex) {
            safeFinishSpan(span, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    public void validatePlan(TaskPlanDTO plan, List<AgentToolDTO> availableTools) {
        taskPlanValidator.validate(plan, availableToolNames(availableTools));
    }

    public List<TeamTaskDTO> schedule(TaskPlanDTO plan) {
        return taskDependencySorter.sort(plan.tasks());
    }

    public List<TeamTaskDTO> scheduleNewTasks(TaskPlanDTO previousPlan, TaskPlanDTO updatedPlan, List<ExecutionResultDTO> executionResults) {
        Set<String> previousTaskIds = taskIds(previousPlan);
        Set<String> completedTaskIds = resultTaskIds(executionResults);
        return taskDependencySorter.sort(updatedPlan.tasks()).stream()
                .filter(task -> {
                    String taskId = task.id() == null ? "" : task.id().trim();
                    return !taskId.isBlank() && !previousTaskIds.contains(taskId) && !completedTaskIds.contains(taskId);
                })
                .toList();
    }

    public TeamTaskDTO findTask(TaskPlanDTO plan, String taskId) {
        return plan.tasks().stream()
                .filter(task -> task.id().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.REQUEST_INVALID, "retry task is not in plan"));
    }

    public TeamTaskExecutionResultDTO executeTask(
            AgentRunCommand command,
            TeamGraphState state,
            TeamTaskDTO task,
            TeamGraphRuntimeContext runtimeContext
    ) {
        if (runtimeContext.limiter() != null) {
            runtimeContext.limiter().checkTimeout();
        }
        emit(runtimeContext, TeamRuntimeEventDTO.taskStart(
                command.traceId(),
                state.step(),
                task.id(),
                "Start task: " + task.name()
        ));
        int step = state.step() + 1;
        if ("TOOL_TASK".equals(task.taskType())) {
            emit(runtimeContext, TeamRuntimeEventDTO.toolCall(command.traceId(), step++, task.id(), task.suggestedTool()));
        }

        TraceSpanDTO taskSpan = safeStartSpan(command.traceId(), runtimeContext.runSpanId(), "team.task.execute", "TEAM",
                objectMapper.createObjectNode()
                        .put("taskId", task.id())
                        .put("taskType", task.taskType()));
        TeamTaskExecutionResultDTO result = executor.execute(new ExecuteTeamTaskCommand(
                command.tenantId(),
                command.userId(),
                command.userInput(),
                task,
                state.context(),
                state.availableTools(),
                state.executionResults()
        ));
        if (runtimeContext.limiter() != null) {
            runtimeContext.limiter().consumeModelCalls(result.modelInvocations().size());
            consumeToolCalls(runtimeContext, result.toolResults());
        }
        recordModelInvocations(command, result.modelInvocations(), spanId(taskSpan));
        String status = result.executionResult().status();
        safeFinishSpan(taskSpan, "SUCCESS".equals(status) ? "SUCCESS" : "FAILED", null, result.executionResult().errorMessage());
        for (AgentToolDispatchResult toolResult : result.toolResults()) {
            emit(runtimeContext, TeamRuntimeEventDTO.toolResult(
                    command.traceId(),
                    step++,
                    task.id(),
                    toolResult.toolName(),
                    toolResult.success() ? "SUCCESS" : "FAILED",
                    toolResult.output()
            ));
        }
        emit(runtimeContext, TeamRuntimeEventDTO.taskResult(
                command.traceId(),
                step,
                task.id(),
                status,
                result.executionResult().result(),
                objectMapper.valueToTree(result.executionResult())
        ));
        return result;
    }

    public TeamReviewResultDTO review(AgentRunCommand command, TeamGraphState state, Long parentSpanId) {
        TraceSpanDTO span = safeStartSpan(command.traceId(), parentSpanId, "team.review", "TEAM",
                objectMapper.createObjectNode());
        try {
            TeamReviewResultDTO result = reviewer.review(new ReviewTeamCommand(
                    command.userInput(),
                    state.plan(),
                    state.executionResults(),
                    state.answerDraft(),
                    state.context()
            ));
            recordModelInvocations(command, result.modelInvocations(), spanId(span));
            safeFinishSpan(span, result.reviewResult().passed() ? "SUCCESS" : "FAILED", null, result.reviewResult().summary());
            return result;
        } catch (Exception ex) {
            safeFinishSpan(span, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    public String buildAnswerDraft(AgentRunCommand command, TeamGraphState state) {
        return answerDraftBuilder.build(command.userInput(), state.plan(), state.executionResults());
    }

    public void emitReview(AgentRunCommand command, TeamGraphRuntimeContext runtimeContext, Integer step, String answerDraft, ReviewResultDTO review) {
        com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
        payload.set("review", objectMapper.valueToTree(review));
        payload.put("answerDraft", answerDraft);
        runtimeContext.eventSink().emit(TeamRuntimeEventDTO.review(
                command.traceId(),
                step,
                review.passed() ? "SUCCESS" : "FAILED",
                review.summary(),
                payload
        ));
    }

    public void emitRetry(AgentRunCommand command, TeamGraphRuntimeContext runtimeContext, Integer step, String taskId, String message) {
        runtimeContext.eventSink().emit(TeamRuntimeEventDTO.retry(command.traceId(), step, taskId, message, null));
    }

    public void emit(TeamGraphRuntimeContext runtimeContext, TeamRuntimeEventDTO event) {
        runtimeContext.eventSink().emit(event);
    }

    public void emitPlan(AgentRunCommand command, TeamGraphRuntimeContext runtimeContext, Integer step, TaskPlanDTO plan) {
        runtimeContext.eventSink().emit(TeamRuntimeEventDTO.plan(
                command.traceId(),
                step,
                "Planner generated task plan",
                objectMapper.valueToTree(plan)
        ));
    }

    private Set<String> availableToolNames(List<AgentToolDTO> tools) {
        if (tools == null || tools.isEmpty()) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (AgentToolDTO tool : tools) {
            if (tool != null && tool.name() != null && !tool.name().isBlank()) {
                names.add(tool.name().trim());
            }
        }
        return names;
    }

    private Set<String> taskIds(TaskPlanDTO plan) {
        if (plan == null || plan.tasks() == null) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (TeamTaskDTO task : plan.tasks()) {
            if (task.id() != null && !task.id().isBlank()) {
                ids.add(task.id().trim());
            }
        }
        return ids;
    }

    private Set<String> resultTaskIds(List<ExecutionResultDTO> results) {
        if (results == null || results.isEmpty()) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (ExecutionResultDTO result : results) {
            if (result.taskId() != null && !result.taskId().isBlank()) {
                ids.add(result.taskId().trim());
            }
        }
        return ids;
    }

    private void consumeToolCalls(TeamGraphRuntimeContext runtimeContext, List<AgentToolDispatchResult> toolResults) {
        if (toolResults == null) {
            return;
        }
        for (AgentToolDispatchResult ignored : toolResults) {
            runtimeContext.limiter().consumeToolCall();
        }
    }

    private TraceSpanDTO safeStartSpan(String traceId, Long parentSpanId, String spanName, String spanType, JsonNode attributes) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        try {
            return traceService.startSpan(new StartTraceSpanCommand(
                    traceId,
                    parentSpanId,
                    spanName,
                    spanType,
                    "core",
                    attributes
            ));
        } catch (Exception ex) {
            return null;
        }
    }

    private void safeFinishSpan(TraceSpanDTO span, String status, String errorCode, String errorMessage) {
        if (span == null || span.id() == null) {
            return;
        }
        try {
            traceService.finishSpan(new FinishTraceSpanCommand(span.id(), status, errorCode, errorMessage, span.attributes()));
        } catch (Exception ex) {
            // Trace is diagnostic data; it must not break the team graph.
        }
    }

    private void recordModelInvocations(AgentRunCommand command, List<ModelInvokeResult> invocations, Long spanId) {
        if (invocations == null) {
            return;
        }
        for (ModelInvokeResult result : invocations) {
            safeRecordTokenUsage(command, result, spanId);
        }
    }

    private void safeRecordTokenUsage(AgentRunCommand command, ModelInvokeResult result, Long spanId) {
        if (command.traceId() == null || command.traceId().isBlank() || result == null || result.usage() == null) {
            return;
        }
        try {
            tokenUsageService.record(new RecordTokenUsageCommand(
                    command.traceId(),
                    spanId,
                    command.tenantId(),
                    command.applicationId(),
                    command.userId(),
                    command.profileId(),
                    result.modelConfigId(),
                    result.providerId(),
                    result.modelName(),
                    result.providerType(),
                    result.usage().promptTokens(),
                    result.usage().completionTokens(),
                    result.usage().totalTokens(),
                    result.usage().estimated()
            ));
        } catch (Exception ex) {
            // Token usage write failure must not break the team graph.
        }
    }

    private Long spanId(TraceSpanDTO span) {
        return span == null ? null : span.id();
    }

    private String errorCode(Exception ex) {
        return ex instanceof BizException bizException ? bizException.getCode() : ErrorCode.INTERNAL_ERROR.getCode();
    }

    private String errorMessage(Exception ex) {
        return ex.getMessage() == null ? "Team graph failed" : ex.getMessage();
    }
}
