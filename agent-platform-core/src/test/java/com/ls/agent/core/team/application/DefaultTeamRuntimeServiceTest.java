package com.ls.agent.core.team.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.dto.AgentRunResult;
import com.ls.agent.core.agent.tool.AgentToolDispatchResult;
import com.ls.agent.core.agent.tool.AgentToolDTO;
import com.ls.agent.core.agent.tool.AgentToolSourceType;
import com.ls.agent.core.agent.tool.AgentToolResolver;
import com.ls.agent.core.context.api.AgentContextBuilder;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.quota.api.TokenUsageService;
import com.ls.agent.core.quota.command.RecordTokenUsageCommand;
import com.ls.agent.core.team.api.TeamEventSink;
import com.ls.agent.core.team.api.TeamExecutor;
import com.ls.agent.core.team.api.TeamPlanner;
import com.ls.agent.core.team.api.TeamReviewer;
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
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceSpanDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultTeamRuntimeServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentContextBuilder contextBuilder = mock(AgentContextBuilder.class);
    private final AgentToolResolver agentToolResolver = mock(AgentToolResolver.class);
    private final TeamPlanner planner = mock(TeamPlanner.class);
    private final TeamExecutor executor = mock(TeamExecutor.class);
    private final TeamReviewer reviewer = mock(TeamReviewer.class);
    private final TeamEventSink eventSink = mock(TeamEventSink.class);
    private final TraceService traceService = mock(TraceService.class);
    private final TokenUsageService tokenUsageService = mock(TokenUsageService.class);
    private final TeamRunLimiter limiter = new TeamRunLimiter(8, 1, 8, 6, 120_000L);
    private final DefaultTeamRuntimeService service = new DefaultTeamRuntimeService(
            contextBuilder,
            agentToolResolver,
            planner,
            executor,
            reviewer,
            new TaskDependencySorter(),
            new TeamAnswerDraftBuilder(),
            new TeamFinalAnswerBuilder(),
            limiter,
            eventSink,
            traceService,
            tokenUsageService,
            objectMapper
    );

    @Test
    void runsPlannerExecutorReviewerAndEmitsEventsInOrder() {
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context("TEAM"));
        when(agentToolResolver.resolve(any())).thenReturn(List.of());
        when(planner.plan(any(PlanTeamCommand.class))).thenReturn(new TeamPlanResultDTO(
                plan(),
                List.of(modelInvocation("plan", 3))
        ));
        when(executor.execute(any(ExecuteTeamTaskCommand.class))).thenReturn(new TeamTaskExecutionResultDTO(
                new ExecutionResultDTO("task-1", "MODEL_TASK", "SUCCESS", "Task result", List.of(), null),
                List.of(modelInvocation("task", 4)),
                List.of()
        ));
        when(reviewer.review(any(ReviewTeamCommand.class))).thenReturn(new TeamReviewResultDTO(
                new ReviewResultDTO(true, List.of(), List.of(), "review passed"),
                List.of(modelInvocation("review", 5))
        ));
        when(traceService.startSpan(any(StartTraceSpanCommand.class)))
                .thenReturn(span(1L, "team.run"))
                .thenReturn(span(2L, "team.plan"))
                .thenReturn(span(3L, "team.task.execute"))
                .thenReturn(span(4L, "team.review"));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.conversationId()).isEqualTo(90001L);
        assertThat(result.assistantMessage()).contains("Task result");
        assertThat(result.usage().totalTokens()).isEqualTo(12);

        ArgumentCaptor<TeamRuntimeEventDTO> eventCaptor = ArgumentCaptor.forClass(TeamRuntimeEventDTO.class);
        verify(eventSink, times(6)).emit(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).extracting(TeamRuntimeEventDTO::type)
                .containsExactly("team_start", "team_plan", "team_task_start", "team_task_result", "team_review", "team_final");

        verify(tokenUsageService, times(3)).record(any(RecordTokenUsageCommand.class));
        verify(planner).plan(any(PlanTeamCommand.class));
        verify(executor).execute(any(ExecuteTeamTaskCommand.class));
        verify(reviewer).review(any(ReviewTeamCommand.class));
    }

    @Test
    void retriesReviewerRequestedTaskOnce() {
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context("TEAM"));
        when(agentToolResolver.resolve(any())).thenReturn(List.of());
        when(planner.plan(any(PlanTeamCommand.class))).thenReturn(new TeamPlanResultDTO(plan(), List.of()));
        when(executor.execute(any(ExecuteTeamTaskCommand.class)))
                .thenReturn(new TeamTaskExecutionResultDTO(
                        new ExecutionResultDTO("task-1", "MODEL_TASK", "SUCCESS", "First result", List.of(), null),
                        List.of(),
                        List.of()
                ))
                .thenReturn(new TeamTaskExecutionResultDTO(
                        new ExecutionResultDTO("task-1", "MODEL_TASK", "SUCCESS", "Retry result", List.of(), null),
                        List.of(),
                        List.of()
                ));
        when(reviewer.review(any(ReviewTeamCommand.class)))
                .thenReturn(new TeamReviewResultDTO(
                        new ReviewResultDTO(false, List.of(), List.of("task-1"), "retry task-1"),
                        List.of(modelInvocation("review-retry", 5))
                ))
                .thenReturn(new TeamReviewResultDTO(
                        new ReviewResultDTO(true, List.of(), List.of(), "review passed"),
                        List.of(modelInvocation("review-final", 7))
                ));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage()).contains("Retry result");
        assertThat(result.usage().totalTokens()).isEqualTo(12);
        verify(executor, times(2)).execute(any(ExecuteTeamTaskCommand.class));
        verify(reviewer, times(2)).review(any(ReviewTeamCommand.class));

        ArgumentCaptor<TeamRuntimeEventDTO> eventCaptor = ArgumentCaptor.forClass(TeamRuntimeEventDTO.class);
        verify(eventSink, times(10)).emit(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).extracting(TeamRuntimeEventDTO::type)
                .containsExactly(
                        "team_start",
                        "team_plan",
                        "team_task_start",
                        "team_task_result",
                        "team_review",
                        "team_retry",
                        "team_task_start",
                        "team_task_result",
                        "team_review",
                        "team_final"
                );
    }

    @Test
    void emitsToolCallAndToolResultAroundToolTask() {
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context("TEAM"));
        when(agentToolResolver.resolve(any())).thenReturn(List.of());
        when(planner.plan(any(PlanTeamCommand.class))).thenReturn(new TeamPlanResultDTO(
                toolPlan(),
                List.of()
        ));
        when(executor.execute(any(ExecuteTeamTaskCommand.class))).thenReturn(new TeamTaskExecutionResultDTO(
                new ExecutionResultDTO("task-tool", "TOOL_TASK", "SUCCESS", "Weather is mild", List.of(), null),
                List.of(),
                List.of(new AgentToolDispatchResult(
                        true,
                        "weather",
                        AgentToolSourceType.SKILL,
                        objectMapper.createObjectNode().put("temperature", "18C"),
                        null
                ))
        ));
        when(reviewer.review(any(ReviewTeamCommand.class))).thenReturn(new TeamReviewResultDTO(
                new ReviewResultDTO(true, List.of(), List.of(), "review passed"),
                List.of()
        ));

        service.run(command(90001L));

        ArgumentCaptor<TeamRuntimeEventDTO> eventCaptor = ArgumentCaptor.forClass(TeamRuntimeEventDTO.class);
        verify(eventSink, times(8)).emit(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).extracting(TeamRuntimeEventDTO::type)
                .containsExactly(
                        "team_start",
                        "team_plan",
                        "team_task_start",
                        "team_tool_call",
                        "team_tool_result",
                        "team_task_result",
                        "team_review",
                        "team_final"
                );
        assertThat(eventCaptor.getAllValues()).extracting(TeamRuntimeEventDTO::step)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }

    private AgentRunCommand command(Long conversationId) {
        return new AgentRunCommand(
                1L,
                10001L,
                20001L,
                50001L,
                conversationId,
                "Plan a team activity",
                "trace-1",
                null,
                null,
                1000
        );
    }

    private AgentContextDTO context(String executionMode) {
        return new AgentContextDTO(
                30001L,
                new ProfileDTO(
                        50001L,
                        20001L,
                        "Team Profile",
                        "GENERAL",
                        "Demo team profile",
                        30001L,
                        "Keep it practical.",
                        objectMapper.createObjectNode(),
                        5,
                        executionMode,
                        "PRIVATE",
                        "DRAFT",
                        List.of(),
                        List.of()
                ),
                List.of(new ModelMessage("system", "You are a team"), new ModelMessage("user", "Plan a team activity")),
                List.of(),
                List.of(),
                20,
                false
        );
    }

    private TaskPlanDTO plan() {
        return new TaskPlanDTO(
                "Plan activity",
                List.of(new TeamTaskDTO(
                        "task-1",
                        "Summarize",
                        "Summarize options.",
                        "MODEL_TASK",
                        null,
                        objectMapper.createObjectNode(),
                        List.of()
                ))
        );
    }

    private TaskPlanDTO toolPlan() {
        return new TaskPlanDTO(
                "Check weather",
                List.of(new TeamTaskDTO(
                        "task-tool",
                        "Fetch weather",
                        "Fetch weather with the weather tool.",
                        "TOOL_TASK",
                        "weather",
                        objectMapper.createObjectNode(),
                        List.of()
                ))
        );
    }

    private ModelInvokeResult modelInvocation(String content, int totalTokens) {
        return new ModelInvokeResult(
                30001L,
                40001L,
                "OPENAI_COMPATIBLE",
                "mock-chat",
                content,
                new ModelUsageDTO(1, totalTokens - 1, totalTokens, true)
        );
    }

    private TraceSpanDTO span(Long id, String spanName) {
        return new TraceSpanDTO(
                id,
                "trace-1",
                null,
                spanName,
                "TEST",
                "core",
                "RUNNING",
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                objectMapper.createObjectNode(),
                LocalDateTime.now()
        );
    }

}
