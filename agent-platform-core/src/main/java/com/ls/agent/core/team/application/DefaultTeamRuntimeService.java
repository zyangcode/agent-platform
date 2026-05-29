package com.ls.agent.core.team.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.dto.AgentRunResult;
import com.ls.agent.core.agent.tool.AgentToolDTO;
import com.ls.agent.core.agent.tool.AgentToolDispatchResult;
import com.ls.agent.core.agent.tool.AgentToolResolver;
import com.ls.agent.core.context.api.AgentContextBuilder;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.quota.api.TokenUsageService;
import com.ls.agent.core.quota.command.RecordTokenUsageCommand;
import com.ls.agent.core.team.api.TeamEventSink;
import com.ls.agent.core.team.api.TeamExecutor;
import com.ls.agent.core.team.api.TeamPlanner;
import com.ls.agent.core.team.api.TeamReviewer;
import com.ls.agent.core.team.api.TeamRuntimeService;
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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DefaultTeamRuntimeService implements TeamRuntimeService {

    private final AgentContextBuilder contextBuilder;
    private final AgentToolResolver agentToolResolver;
    private final TeamPlanner planner;
    private final TeamExecutor executor;
    private final TeamReviewer reviewer;
    private final TaskDependencySorter taskDependencySorter;
    private final TeamAnswerDraftBuilder answerDraftBuilder;
    private final TeamFinalAnswerBuilder finalAnswerBuilder;
    private final TeamRunLimiter limiterTemplate;
    private final TeamEventSink eventSink;
    private final TraceService traceService;
    private final TokenUsageService tokenUsageService;
    private final ObjectMapper objectMapper;

    public DefaultTeamRuntimeService(
            AgentContextBuilder contextBuilder,
            AgentToolResolver agentToolResolver,
            TeamPlanner planner,
            TeamExecutor executor,
            TeamReviewer reviewer,
            TaskDependencySorter taskDependencySorter,
            TeamAnswerDraftBuilder answerDraftBuilder,
            TeamFinalAnswerBuilder finalAnswerBuilder,
            TeamRunLimiter limiterTemplate,
            TeamEventSink eventSink,
            TraceService traceService,
            TokenUsageService tokenUsageService,
            ObjectMapper objectMapper
    ) {
        this.contextBuilder = contextBuilder;
        this.agentToolResolver = agentToolResolver;
        this.planner = planner;
        this.executor = executor;
        this.reviewer = reviewer;
        this.taskDependencySorter = taskDependencySorter;
        this.answerDraftBuilder = answerDraftBuilder;
        this.finalAnswerBuilder = finalAnswerBuilder;
        this.limiterTemplate = limiterTemplate;
        this.eventSink = eventSink;
        this.traceService = traceService;
        this.tokenUsageService = tokenUsageService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentRunResult run(AgentRunCommand command) {
        return run(command, eventSink);
    }

    @Override
    public AgentRunResult run(AgentRunCommand command, TeamEventSink eventSink) {
        validate(command);
        TeamEventSink activeEventSink = eventSink == null ? this.eventSink : eventSink;
        TeamRunLimiter limiter = limiterTemplate.newRun();
        TraceSpanDTO runSpan = safeStartSpan(command.traceId(), null, "team.run", "TEAM",
                objectMapper.createObjectNode().put("profileId", command.profileId()));
        int step = 1;
        emit(activeEventSink, TeamRuntimeEventDTO.start(command.traceId(), step++, "Team run started", null));
        try {
            requireNonNull(command.conversationId(), "conversationId");
            Long conversationId = command.conversationId();
            AgentContextDTO context = buildContext(command, conversationId, runSpan == null ? null : runSpan.id());
            List<AgentToolDTO> tools = agentToolResolver.resolve(context);

            TraceSpanDTO planSpan = safeStartSpan(command.traceId(), spanId(runSpan), "team.plan", "TEAM",
                    objectMapper.createObjectNode());
            TeamPlanResultDTO planResult = planner.plan(new PlanTeamCommand(command.userInput(), context, tools));
            List<TeamPlanResultDTO> planResults = new ArrayList<>();
            planResults.add(planResult);
            limiter.consumeModelCalls(planResult.modelInvocations().size());
            recordModelInvocations(command, planResult.modelInvocations(), spanId(planSpan));
            safeFinishSpan(planSpan, "SUCCESS", null, null);

            TaskPlanDTO plan = planResult.plan();
            limiter.checkTaskCount(plan.tasks().size());
            emit(activeEventSink, TeamRuntimeEventDTO.plan(command.traceId(), step++, "Planner 已生成任务计划", objectMapper.valueToTree(plan)));

            TaskExecutionBatch taskExecutionBatch = executeTasks(command, context, tools, plan, limiter, spanId(runSpan), step, activeEventSink);
            List<TeamTaskExecutionResultDTO> taskExecutionResults = taskExecutionBatch.taskResults();
            List<ExecutionResultDTO> executionResults = executionResults(taskExecutionResults);
            step += taskExecutionBatch.emittedEventCount();

            String answerDraft = answerDraftBuilder.build(command.userInput(), plan, executionResults);
            TeamReviewResultDTO reviewResult = review(command, context, plan, executionResults, answerDraft, limiter, spanId(runSpan));
            List<TeamReviewResultDTO> reviewResults = new ArrayList<>();
            reviewResults.add(reviewResult);
            step = emitReview(command, step, answerDraft, reviewResult.reviewResult(), activeEventSink);

            if (needsRetry(reviewResult.reviewResult())) {
                limiter.consumeRetry();
                TeamTaskDTO retryTask = findTask(plan, reviewResult.reviewResult().retryTasks().get(0));
                emit(activeEventSink, TeamRuntimeEventDTO.retry(command.traceId(), step++, retryTask.id(), "Reviewer 要求重试任务", null));
                TaskExecutionOutcome retried = executeTask(command, context, tools, retryTask, executionResults, limiter, spanId(runSpan), step, activeEventSink);
                step += retried.emittedEventCount();
                TeamTaskExecutionResultDTO retriedResult = retried.taskResult();
                replaceResult(executionResults, retriedResult.executionResult());
                taskExecutionResults.add(retriedResult);
                answerDraft = answerDraftBuilder.build(command.userInput(), plan, executionResults);
                reviewResult = review(command, context, plan, executionResults, answerDraft, limiter, spanId(runSpan));
                reviewResults.add(reviewResult);
                step = emitReview(command, step, answerDraft, reviewResult.reviewResult(), activeEventSink);
            } else if (needsReplan(reviewResult.reviewResult())) {
                limiter.consumeRetry();
                emit(activeEventSink, TeamRuntimeEventDTO.retry(command.traceId(), step++, null, "Reviewer 要求重新规划", null));
                TraceSpanDTO replanSpan = safeStartSpan(command.traceId(), spanId(runSpan), "team.plan", "TEAM",
                        objectMapper.createObjectNode().put("reason", "reviewer_replan"));
                TeamPlanResultDTO replanResult = planner.plan(new PlanTeamCommand(
                        command.userInput(),
                        context,
                        tools,
                        plan,
                        executionResults,
                        reviewResult.reviewResult()
                ));
                planResults.add(replanResult);
                limiter.consumeModelCalls(replanResult.modelInvocations().size());
                recordModelInvocations(command, replanResult.modelInvocations(), spanId(replanSpan));
                safeFinishSpan(replanSpan, "SUCCESS", null, null);

                TaskPlanDTO previousPlan = plan;
                plan = replanResult.plan();
                limiter.checkTaskCount(plan.tasks().size());
                emit(activeEventSink, TeamRuntimeEventDTO.plan(command.traceId(), step++, "Planner 已生成更新后的任务计划", objectMapper.valueToTree(plan)));
                TaskExecutionBatch newTaskBatch = executeNewTasks(command, context, tools, previousPlan, plan, executionResults, limiter, spanId(runSpan), step, activeEventSink);
                taskExecutionResults.addAll(newTaskBatch.taskResults());
                for (TeamTaskExecutionResultDTO taskResult : newTaskBatch.taskResults()) {
                    replaceResult(executionResults, taskResult.executionResult());
                }
                step += newTaskBatch.emittedEventCount();
                answerDraft = answerDraftBuilder.build(command.userInput(), plan, executionResults);
                reviewResult = review(command, context, plan, executionResults, answerDraft, limiter, spanId(runSpan));
                reviewResults.add(reviewResult);
                step = emitReview(command, step, answerDraft, reviewResult.reviewResult(), activeEventSink);
            }

            String finalAnswer = finalAnswerBuilder.build(answerDraft, reviewResult.reviewResult());
            emit(activeEventSink, TeamRuntimeEventDTO.finalAnswer(command.traceId(), step, "Team 已生成最终答案", null));
            ModelUsageDTO totalUsage = totalUsage(planResults, taskExecutionResults, reviewResults);
            safeFinishSpan(runSpan, "SUCCESS", null, null);
            return new AgentRunResult(conversationId, finalAnswer, totalUsage);
        } catch (Exception ex) {
            safeFinishSpan(runSpan, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    private TaskExecutionBatch executeTasks(
            AgentRunCommand command,
            AgentContextDTO context,
            List<AgentToolDTO> tools,
            TaskPlanDTO plan,
            TeamRunLimiter limiter,
            Long parentSpanId,
            int step,
            TeamEventSink eventSink
    ) {
        List<TeamTaskExecutionResultDTO> taskResults = new ArrayList<>();
        List<ExecutionResultDTO> results = new ArrayList<>();
        int emittedEventCount = 0;
        for (TeamTaskDTO task : taskDependencySorter.sort(plan.tasks())) {
            TaskExecutionOutcome outcome = executeTask(command, context, tools, task, results, limiter, parentSpanId, step, eventSink);
            taskResults.add(outcome.taskResult());
            results.add(outcome.taskResult().executionResult());
            step += outcome.emittedEventCount();
            emittedEventCount += outcome.emittedEventCount();
        }
        return new TaskExecutionBatch(taskResults, emittedEventCount);
    }

    private TaskExecutionBatch executeNewTasks(
            AgentRunCommand command,
            AgentContextDTO context,
            List<AgentToolDTO> tools,
            TaskPlanDTO previousPlan,
            TaskPlanDTO updatedPlan,
            List<ExecutionResultDTO> executionResults,
            TeamRunLimiter limiter,
            Long parentSpanId,
            int step,
            TeamEventSink eventSink
    ) {
        Set<String> previousTaskIds = taskIds(previousPlan);
        Set<String> completedTaskIds = resultTaskIds(executionResults);
        List<TeamTaskExecutionResultDTO> taskResults = new ArrayList<>();
        int emittedEventCount = 0;
        for (TeamTaskDTO task : taskDependencySorter.sort(updatedPlan.tasks())) {
            String taskId = task.id() == null ? "" : task.id().trim();
            if (taskId.isBlank() || previousTaskIds.contains(taskId) || completedTaskIds.contains(taskId)) {
                continue;
            }
            TaskExecutionOutcome outcome = executeTask(command, context, tools, task, executionResults, limiter, parentSpanId, step, eventSink);
            taskResults.add(outcome.taskResult());
            executionResults.add(outcome.taskResult().executionResult());
            completedTaskIds.add(taskId);
            step += outcome.emittedEventCount();
            emittedEventCount += outcome.emittedEventCount();
        }
        return new TaskExecutionBatch(taskResults, emittedEventCount);
    }

    private TaskExecutionOutcome executeTask(
            AgentRunCommand command,
            AgentContextDTO context,
            List<AgentToolDTO> tools,
            TeamTaskDTO task,
            List<ExecutionResultDTO> previousResults,
            TeamRunLimiter limiter,
            Long parentSpanId,
            int step,
            TeamEventSink eventSink
    ) {
        limiter.checkTimeout();
        int emittedEventCount = 0;
        emit(eventSink, TeamRuntimeEventDTO.taskStart(command.traceId(), step + emittedEventCount++, task.id(), "开始任务：" + task.name()));
        if ("TOOL_TASK".equals(task.taskType())) {
            emit(eventSink, TeamRuntimeEventDTO.toolCall(command.traceId(), step + emittedEventCount++, task.id(), task.suggestedTool()));
        }
        TraceSpanDTO taskSpan = safeStartSpan(command.traceId(), parentSpanId, "team.task.execute", "TEAM",
                objectMapper.createObjectNode()
                        .put("taskId", task.id())
                        .put("taskType", task.taskType()));
        TeamTaskExecutionResultDTO result = executor.execute(new ExecuteTeamTaskCommand(
                command.tenantId(),
                command.userId(),
                command.userInput(),
                task,
                context,
                tools,
                previousResults
        ));
        limiter.consumeModelCalls(result.modelInvocations().size());
        consumeToolCalls(limiter, result.toolResults());
        recordModelInvocations(command, result.modelInvocations(), spanId(taskSpan));
        String status = result.executionResult().status();
        safeFinishSpan(taskSpan, "SUCCESS".equals(status) ? "SUCCESS" : "FAILED", null, result.executionResult().errorMessage());
        emittedEventCount += emitToolResults(command, step + emittedEventCount, task, result.toolResults(), eventSink);
        emit(eventSink, TeamRuntimeEventDTO.taskResult(
                command.traceId(),
                step + emittedEventCount++,
                task.id(),
                status,
                result.executionResult().result(),
                objectMapper.valueToTree(result.executionResult())
        ));
        return new TaskExecutionOutcome(result, emittedEventCount);
    }

    private TeamReviewResultDTO review(
            AgentRunCommand command,
            AgentContextDTO context,
            TaskPlanDTO plan,
            List<ExecutionResultDTO> executionResults,
            String answerDraft,
            TeamRunLimiter limiter,
            Long parentSpanId
    ) {
        TraceSpanDTO span = safeStartSpan(command.traceId(), parentSpanId, "team.review", "TEAM",
                objectMapper.createObjectNode());
        TeamReviewResultDTO result = reviewer.review(new ReviewTeamCommand(
                command.userInput(),
                plan,
                executionResults,
                answerDraft,
                context
        ));
        limiter.consumeModelCalls(result.modelInvocations().size());
        recordModelInvocations(command, result.modelInvocations(), spanId(span));
        safeFinishSpan(span, result.reviewResult().passed() ? "SUCCESS" : "FAILED", null, result.reviewResult().summary());
        return result;
    }

    private int emitReview(
            AgentRunCommand command,
            int step,
            String answerDraft,
            ReviewResultDTO review,
            TeamEventSink eventSink
    ) {
        JsonNode payload = objectMapper.createObjectNode()
                .set("review", objectMapper.valueToTree(review));
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).put("answerDraft", answerDraft);
        emit(eventSink, TeamRuntimeEventDTO.review(
                command.traceId(),
                step++,
                review.passed() ? "SUCCESS" : "FAILED",
                review.summary(),
                payload
        ));
        return step;
    }

    private void consumeToolCalls(TeamRunLimiter limiter, List<AgentToolDispatchResult> toolResults) {
        if (toolResults == null) {
            return;
        }
        for (AgentToolDispatchResult ignored : toolResults) {
            limiter.consumeToolCall();
        }
    }

    private int emitToolResults(
            AgentRunCommand command,
            int step,
            TeamTaskDTO task,
            List<AgentToolDispatchResult> toolResults,
            TeamEventSink eventSink
    ) {
        if (toolResults == null || toolResults.isEmpty()) {
            return 0;
        }
        int emittedEventCount = 0;
        for (AgentToolDispatchResult toolResult : toolResults) {
            emit(eventSink, TeamRuntimeEventDTO.toolResult(
                    command.traceId(),
                    step + emittedEventCount++,
                    task.id(),
                    toolResult.toolName(),
                    toolResult.success() ? "SUCCESS" : "FAILED",
                    toolResult.output()
            ));
        }
        return emittedEventCount;
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

    private TeamTaskDTO findTask(TaskPlanDTO plan, String taskId) {
        return plan.tasks().stream()
                .filter(task -> task.id().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.REQUEST_INVALID, "retry task is not in plan"));
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

    private List<ExecutionResultDTO> executionResults(List<TeamTaskExecutionResultDTO> taskExecutionResults) {
        return new ArrayList<>(taskExecutionResults.stream()
                .map(TeamTaskExecutionResultDTO::executionResult)
                .toList());
    }

    private Set<String> taskIds(TaskPlanDTO plan) {
        if (plan == null || plan.tasks() == null) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (TeamTaskDTO task : plan.tasks()) {
            if (task.id() != null && !task.id().isBlank()) {
                ids.add(task.id().trim());
            }
        }
        return ids;
    }

    private Set<String> resultTaskIds(List<ExecutionResultDTO> results) {
        if (results == null || results.isEmpty()) {
            return new HashSet<>();
        }
        Set<String> ids = new HashSet<>();
        for (ExecutionResultDTO result : results) {
            if (result.taskId() != null && !result.taskId().isBlank()) {
                ids.add(result.taskId().trim());
            }
        }
        return ids;
    }

    private AgentContextDTO buildContext(AgentRunCommand command, Long conversationId, Long parentSpanId) {
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

    private void validate(AgentRunCommand command) {
        requireNonNull(command.tenantId(), "tenantId");
        requireNonNull(command.userId(), "userId");
        requireNonNull(command.applicationId(), "applicationId");
        requireNonNull(command.profileId(), "profileId");
        if (command.userInput() == null || command.userInput().isBlank()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "userInput is required");
        }
    }

    private void requireNonNull(Object value, String field) {
        if (value == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, field + " is required");
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
            traceService.finishSpan(new FinishTraceSpanCommand(span.id(), status, errorCode, errorMessage));
        } catch (Exception ex) {
            // Trace is diagnostic data; it must not break the team run.
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
            // Token usage write failure must not break the team run.
        }
    }

    private ModelUsageDTO totalUsage(
            List<TeamPlanResultDTO> planResults,
            List<TeamTaskExecutionResultDTO> taskExecutionResults,
            List<TeamReviewResultDTO> reviewResults
    ) {
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        boolean estimated = false;
        for (TeamPlanResultDTO planResult : planResults) {
            for (ModelInvokeResult invocation : planResult.modelInvocations()) {
                ModelUsageDTO usage = invocation.usage();
                if (usage != null) {
                    promptTokens += usage.promptTokens();
                    completionTokens += usage.completionTokens();
                    totalTokens += usage.totalTokens();
                    estimated = estimated || usage.estimated();
                }
            }
        }
        for (TeamTaskExecutionResultDTO taskResult : taskExecutionResults) {
            for (ModelInvokeResult invocation : taskResult.modelInvocations()) {
                ModelUsageDTO usage = invocation.usage();
                if (usage != null) {
                    promptTokens += usage.promptTokens();
                    completionTokens += usage.completionTokens();
                    totalTokens += usage.totalTokens();
                    estimated = estimated || usage.estimated();
                }
            }
        }
        for (TeamReviewResultDTO reviewResult : reviewResults) {
            for (ModelInvokeResult invocation : reviewResult.modelInvocations()) {
                ModelUsageDTO usage = invocation.usage();
                if (usage != null) {
                    promptTokens += usage.promptTokens();
                    completionTokens += usage.completionTokens();
                    totalTokens += usage.totalTokens();
                    estimated = estimated || usage.estimated();
                }
            }
        }
        return new ModelUsageDTO(promptTokens, completionTokens, totalTokens, estimated);
    }

    private Long spanId(TraceSpanDTO span) {
        return span == null ? null : span.id();
    }

    private void emit(TeamEventSink eventSink, TeamRuntimeEventDTO event) {
        eventSink.emit(event);
    }

    private String errorCode(Exception ex) {
        return ex instanceof BizException bizException ? bizException.getCode() : ErrorCode.INTERNAL_ERROR.getCode();
    }

    private String errorMessage(Exception ex) {
        return ex.getMessage() == null ? "Team runtime failed" : ex.getMessage();
    }

    private record TaskExecutionBatch(
            List<TeamTaskExecutionResultDTO> taskResults,
            int emittedEventCount
    ) {
    }

    private record TaskExecutionOutcome(
            TeamTaskExecutionResultDTO taskResult,
            int emittedEventCount
    ) {
    }
}
