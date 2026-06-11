package com.ls.agent.core.team.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.tool.AgentToolDispatchResult;
import com.ls.agent.core.agent.tool.AgentToolDTO;
import com.ls.agent.core.agent.tool.AgentToolResolver;
import com.ls.agent.core.agent.tool.AgentToolRiskLevel;
import com.ls.agent.core.agent.tool.AgentToolSourceType;
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
import com.ls.agent.core.team.application.TaskDependencySorter;
import com.ls.agent.core.team.application.TeamAnswerDraftBuilder;
import com.ls.agent.core.team.application.TaskPlanValidator;
import com.ls.agent.core.team.application.TeamRunLimiter;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamGraphFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void invokesMinimalGraphAndReturnsFinalState() {
        TeamGraphFactory factory = new TeamGraphFactory();
        AgentRunCommand command = new AgentRunCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "Plan a team activity",
                "trace-1",
                null,
                null,
                1000
        );
        TeamGraphState initialState = TeamGraphState.initial(command, 70001L);

        TeamGraphState finalState = factory.invoke(initialState, TeamGraphRuntimeContext.noop());

        assertThat(finalState.command()).isEqualTo(command);
        assertThat(finalState.conversationId()).isEqualTo(90001L);
        assertThat(finalState.runSpanId()).isEqualTo(70001L);
        assertThat(finalState.route()).isEqualTo(TeamGraphRoute.FINAL);
    }

    @Test
    void runsBuildContextPlanValidateScheduleAndExecuteBatchNodes() {
        AgentContextBuilder contextBuilder = mock(AgentContextBuilder.class);
        AgentToolResolver agentToolResolver = mock(AgentToolResolver.class);
        TeamPlanner planner = mock(TeamPlanner.class);
        TeamExecutor executor = mock(TeamExecutor.class);
        TeamReviewer reviewer = mock(TeamReviewer.class);
        TraceService traceService = mock(TraceService.class);
        TokenUsageService tokenUsageService = mock(TokenUsageService.class);
        TeamEventSink eventSink = mock(TeamEventSink.class);

        AgentContextDTO context = context();
        AgentToolDTO weatherTool = tool("weather");
        TaskPlanDTO plan = planWithDependency();
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context);
        when(agentToolResolver.resolve(context)).thenReturn(List.of(weatherTool));
        when(planner.plan(any(PlanTeamCommand.class))).thenReturn(new TeamPlanResultDTO(
                plan,
                List.of(modelInvocation("plan", 3))
        ));
        when(executor.execute(any(ExecuteTeamTaskCommand.class)))
                .thenReturn(new TeamTaskExecutionResultDTO(
                        new ExecutionResultDTO("task-1", "TOOL_TASK", "SUCCESS", "Weather is mild", List.of("weather"), null),
                        List.of(),
                        List.of()
                ))
                .thenReturn(new TeamTaskExecutionResultDTO(
                        new ExecutionResultDTO("task-2", "MODEL_TASK", "SUCCESS", "Use mild weather plan", List.of(), null),
                        List.of(),
                        List.of()
                ));
        when(reviewer.review(any(ReviewTeamCommand.class))).thenReturn(new TeamReviewResultDTO(
                new ReviewResultDTO(true, List.of(), List.of(), "review passed"),
                List.of()
        ));

        TeamGraphSupport support = new TeamGraphSupport(
                contextBuilder,
                agentToolResolver,
                planner,
                executor,
                reviewer,
                new TeamAnswerDraftBuilder(),
                new TaskPlanValidator(),
                new TaskDependencySorter(),
                traceService,
                tokenUsageService,
                objectMapper
        );
        TeamGraphFactory factory = new TeamGraphFactory(support);
        AgentRunCommand command = command();

        TeamGraphState finalState = factory.invoke(
                TeamGraphState.initial(command, 70001L),
                new TeamGraphRuntimeContext(eventSink, new TeamRunLimiter(), 70001L)
        );

        assertThat(finalState.context()).isEqualTo(context);
        assertThat(finalState.availableTools()).containsExactly(weatherTool);
        assertThat(finalState.plan()).isEqualTo(plan);
        assertThat(finalState.planResults()).hasSize(1);
        assertThat(finalState.scheduledTasks()).extracting(TeamTaskDTO::id)
                .containsExactly("task-1", "task-2");
        assertThat(finalState.review().passed()).isTrue();
        assertThat(finalState.route()).isEqualTo(TeamGraphRoute.FINAL);
        assertThat(finalState.step()).isEqualTo(8);

        verify(contextBuilder).build(any(BuildAgentContextCommand.class));
        verify(agentToolResolver).resolve(context);
        verify(planner).plan(any(PlanTeamCommand.class));
        verify(tokenUsageService).record(any(RecordTokenUsageCommand.class));

        ArgumentCaptor<TeamRuntimeEventDTO> eventCaptor = ArgumentCaptor.forClass(TeamRuntimeEventDTO.class);
        verify(eventSink, times(7)).emit(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues().get(0).type()).isEqualTo(TeamRuntimeEventDTO.TYPE_TEAM_PLAN);
        assertThat(eventCaptor.getAllValues().get(0).step()).isEqualTo(1);
    }

    @Test
    void runsExecuteBatchNodeAndEmitsTaskAndToolEventsInOrder() {
        AgentContextBuilder contextBuilder = mock(AgentContextBuilder.class);
        AgentToolResolver agentToolResolver = mock(AgentToolResolver.class);
        TeamPlanner planner = mock(TeamPlanner.class);
        TeamExecutor executor = mock(TeamExecutor.class);
        TeamReviewer reviewer = mock(TeamReviewer.class);
        TraceService traceService = mock(TraceService.class);
        TokenUsageService tokenUsageService = mock(TokenUsageService.class);
        TeamEventSink eventSink = mock(TeamEventSink.class);

        AgentContextDTO context = context();
        AgentToolDTO weatherTool = tool("weather");
        TaskPlanDTO plan = planWithDependency();
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context);
        when(agentToolResolver.resolve(context)).thenReturn(List.of(weatherTool));
        when(planner.plan(any(PlanTeamCommand.class))).thenReturn(new TeamPlanResultDTO(plan, List.of()));
        when(executor.execute(any(ExecuteTeamTaskCommand.class)))
                .thenReturn(new TeamTaskExecutionResultDTO(
                        new ExecutionResultDTO("task-1", "TOOL_TASK", "SUCCESS", "Weather is mild", List.of("weather"), null),
                        List.of(modelInvocation("tool task", 4)),
                        List.of(new AgentToolDispatchResult(
                                true,
                                "weather",
                                AgentToolSourceType.SKILL,
                                objectMapper.createObjectNode().put("temperature", "18C"),
                                null
                        ))
                ))
                .thenReturn(new TeamTaskExecutionResultDTO(
                        new ExecutionResultDTO("task-2", "MODEL_TASK", "SUCCESS", "Use mild weather plan", List.of(), null),
                        List.of(modelInvocation("model task", 5)),
                        List.of()
                ));
        when(reviewer.review(any(ReviewTeamCommand.class))).thenReturn(new TeamReviewResultDTO(
                new ReviewResultDTO(true, List.of(), List.of(), "review passed"),
                List.of(modelInvocation("review", 6))
        ));

        TeamGraphSupport support = new TeamGraphSupport(
                contextBuilder,
                agentToolResolver,
                planner,
                executor,
                reviewer,
                new TeamAnswerDraftBuilder(),
                new TaskPlanValidator(),
                new TaskDependencySorter(),
                traceService,
                tokenUsageService,
                objectMapper
        );
        TeamGraphFactory factory = new TeamGraphFactory(support);

        TeamGraphState finalState = factory.invoke(
                TeamGraphState.initial(command(), 70001L),
                new TeamGraphRuntimeContext(eventSink, new TeamRunLimiter(), 70001L)
        );

        assertThat(finalState.taskExecutionResults()).hasSize(2);
        assertThat(finalState.executionResults()).extracting(ExecutionResultDTO::taskId)
                .containsExactly("task-1", "task-2");
        assertThat(finalState.review().passed()).isTrue();
        assertThat(finalState.reviewResults()).hasSize(1);
        assertThat(finalState.route()).isEqualTo(TeamGraphRoute.FINAL);
        assertThat(finalState.step()).isEqualTo(9);
        verify(executor, times(2)).execute(any(ExecuteTeamTaskCommand.class));
        verify(reviewer).review(any(ReviewTeamCommand.class));
        verify(tokenUsageService, times(3)).record(any(RecordTokenUsageCommand.class));

        ArgumentCaptor<TeamRuntimeEventDTO> eventCaptor = ArgumentCaptor.forClass(TeamRuntimeEventDTO.class);
        verify(eventSink, times(8)).emit(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).extracting(TeamRuntimeEventDTO::type)
                .containsExactly(
                        TeamRuntimeEventDTO.TYPE_TEAM_PLAN,
                        TeamRuntimeEventDTO.TYPE_TEAM_TASK_START,
                        TeamRuntimeEventDTO.TYPE_TEAM_TOOL_CALL,
                        TeamRuntimeEventDTO.TYPE_TEAM_TOOL_RESULT,
                        TeamRuntimeEventDTO.TYPE_TEAM_TASK_RESULT,
                        TeamRuntimeEventDTO.TYPE_TEAM_TASK_START,
                        TeamRuntimeEventDTO.TYPE_TEAM_TASK_RESULT,
                        TeamRuntimeEventDTO.TYPE_TEAM_REVIEW
                );
        assertThat(eventCaptor.getAllValues()).extracting(TeamRuntimeEventDTO::step)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    void retriesRequestedTaskAndReviewsAgain() {
        TeamEventSink eventSink = mock(TeamEventSink.class);
        TaskPlanDTO plan = planWithDependency();
        TeamGraphFactory factory = new TeamGraphFactory(supportForRetryLoop(plan));

        TeamGraphState finalState = factory.invoke(
                TeamGraphState.initial(command(), 70001L),
                new TeamGraphRuntimeContext(eventSink, new TeamRunLimiter(), 70001L)
        );

        assertThat(finalState.route()).isEqualTo(TeamGraphRoute.FINAL);
        assertThat(finalState.taskExecutionResults()).hasSize(3);
        assertThat(finalState.executionResults()).extracting(ExecutionResultDTO::taskId)
                .containsExactly("task-1", "task-2");
        assertThat(finalState.executionResults().get(0).result()).isEqualTo("Retry weather result");
        assertThat(finalState.reviewResults()).hasSize(2);
        assertThat(finalState.step()).isEqualTo(13);

        ArgumentCaptor<TeamRuntimeEventDTO> eventCaptor = ArgumentCaptor.forClass(TeamRuntimeEventDTO.class);
        verify(eventSink, times(12)).emit(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).extracting(TeamRuntimeEventDTO::type)
                .containsExactly(
                        TeamRuntimeEventDTO.TYPE_TEAM_PLAN,
                        TeamRuntimeEventDTO.TYPE_TEAM_TASK_START,
                        TeamRuntimeEventDTO.TYPE_TEAM_TOOL_CALL,
                        TeamRuntimeEventDTO.TYPE_TEAM_TASK_RESULT,
                        TeamRuntimeEventDTO.TYPE_TEAM_TASK_START,
                        TeamRuntimeEventDTO.TYPE_TEAM_TASK_RESULT,
                        TeamRuntimeEventDTO.TYPE_TEAM_REVIEW,
                        TeamRuntimeEventDTO.TYPE_TEAM_RETRY,
                        TeamRuntimeEventDTO.TYPE_TEAM_TASK_START,
                        TeamRuntimeEventDTO.TYPE_TEAM_TOOL_CALL,
                        TeamRuntimeEventDTO.TYPE_TEAM_TASK_RESULT,
                        TeamRuntimeEventDTO.TYPE_TEAM_REVIEW
                );
        assertThat(eventCaptor.getAllValues().get(7).taskId()).isEqualTo("task-1");
    }

    @Test
    void replansAndExecutesOnlyNewTasksBeforeReviewingAgain() {
        TeamEventSink eventSink = mock(TeamEventSink.class);
        TaskPlanDTO plan = planWithDependency();
        TaskPlanDTO replanned = replannedPlanWithNewTask();
        TeamGraphFactory factory = new TeamGraphFactory(supportForReplanLoop(plan, replanned));

        TeamGraphState finalState = factory.invoke(
                TeamGraphState.initial(command(), 70001L),
                new TeamGraphRuntimeContext(eventSink, new TeamRunLimiter(), 70001L)
        );

        assertThat(finalState.route()).isEqualTo(TeamGraphRoute.FINAL);
        assertThat(finalState.previousPlan()).isEqualTo(plan);
        assertThat(finalState.plan()).isEqualTo(replanned);
        assertThat(finalState.scheduledTasks()).extracting(TeamTaskDTO::id).containsExactly("task-3");
        assertThat(finalState.executionResults()).extracting(ExecutionResultDTO::taskId)
                .containsExactly("task-1", "task-2", "task-3");
        assertThat(finalState.reviewResults()).hasSize(2);
        assertThat(finalState.step()).isEqualTo(13);

        ArgumentCaptor<TeamRuntimeEventDTO> eventCaptor = ArgumentCaptor.forClass(TeamRuntimeEventDTO.class);
        verify(eventSink, times(12)).emit(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues().get(7).type()).isEqualTo(TeamRuntimeEventDTO.TYPE_TEAM_RETRY);
        assertThat(eventCaptor.getAllValues().get(7).taskId()).isNull();
        assertThat(eventCaptor.getAllValues()).extracting(TeamRuntimeEventDTO::type)
                .containsExactly(
                        TeamRuntimeEventDTO.TYPE_TEAM_PLAN,
                        TeamRuntimeEventDTO.TYPE_TEAM_TASK_START,
                        TeamRuntimeEventDTO.TYPE_TEAM_TOOL_CALL,
                        TeamRuntimeEventDTO.TYPE_TEAM_TASK_RESULT,
                        TeamRuntimeEventDTO.TYPE_TEAM_TASK_START,
                        TeamRuntimeEventDTO.TYPE_TEAM_TASK_RESULT,
                        TeamRuntimeEventDTO.TYPE_TEAM_REVIEW,
                        TeamRuntimeEventDTO.TYPE_TEAM_RETRY,
                        TeamRuntimeEventDTO.TYPE_TEAM_PLAN,
                        TeamRuntimeEventDTO.TYPE_TEAM_TASK_START,
                        TeamRuntimeEventDTO.TYPE_TEAM_TASK_RESULT,
                        TeamRuntimeEventDTO.TYPE_TEAM_REVIEW
                );
    }

    private AgentRunCommand command() {
        return new AgentRunCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "Plan a team activity",
                "trace-1",
                null,
                null,
                1000
        );
    }

    private AgentContextDTO context() {
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
                        "TEAM",
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

    private TaskPlanDTO planWithDependency() {
        return new TaskPlanDTO(
                "Plan activity",
                List.of(
                        new TeamTaskDTO(
                                "task-2",
                                "Use weather",
                                "Use the weather result.",
                                "MODEL_TASK",
                                null,
                                objectMapper.createObjectNode(),
                                List.of("task-1")
                        ),
                        new TeamTaskDTO(
                                "task-1",
                                "Fetch weather",
                                "Fetch weather with the weather tool.",
                                "TOOL_TASK",
                                "weather",
                                objectMapper.createObjectNode(),
                                List.of()
                        )
                )
        );
    }

    private TaskPlanDTO replannedPlanWithNewTask() {
        return new TaskPlanDTO(
                "Plan activity with budget",
                List.of(
                        new TeamTaskDTO(
                                "task-2",
                                "Use weather",
                                "Use the weather result.",
                                "MODEL_TASK",
                                null,
                                objectMapper.createObjectNode(),
                                List.of("task-1")
                        ),
                        new TeamTaskDTO(
                                "task-1",
                                "Fetch weather",
                                "Fetch weather with the weather tool.",
                                "TOOL_TASK",
                                "weather",
                                objectMapper.createObjectNode(),
                                List.of()
                        ),
                        new TeamTaskDTO(
                                "task-3",
                                "Add budget",
                                "Add a budget estimate.",
                                "MODEL_TASK",
                                null,
                                objectMapper.createObjectNode(),
                                List.of("task-2")
                        )
                )
        );
    }

    private AgentToolDTO tool(String name) {
        return new AgentToolDTO(
                name,
                name,
                "Tool " + name,
                AgentToolSourceType.SKILL,
                objectMapper.createObjectNode(),
                AgentToolRiskLevel.LOW
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

    private TeamGraphSupport supportForReview(ReviewResultDTO review, TaskPlanDTO plan) {
        AgentContextBuilder contextBuilder = mock(AgentContextBuilder.class);
        AgentToolResolver agentToolResolver = mock(AgentToolResolver.class);
        TeamPlanner planner = mock(TeamPlanner.class);
        TeamExecutor executor = mock(TeamExecutor.class);
        TeamReviewer reviewer = mock(TeamReviewer.class);
        TraceService traceService = mock(TraceService.class);
        TokenUsageService tokenUsageService = mock(TokenUsageService.class);

        AgentContextDTO context = context();
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context);
        when(agentToolResolver.resolve(context)).thenReturn(List.of(tool("weather")));
        when(planner.plan(any(PlanTeamCommand.class))).thenReturn(new TeamPlanResultDTO(plan, List.of()));
        when(executor.execute(any(ExecuteTeamTaskCommand.class)))
                .thenReturn(new TeamTaskExecutionResultDTO(
                        new ExecutionResultDTO("task-1", "TOOL_TASK", "SUCCESS", "Weather is mild", List.of("weather"), null),
                        List.of(),
                        List.of()
                ))
                .thenReturn(new TeamTaskExecutionResultDTO(
                        new ExecutionResultDTO("task-2", "MODEL_TASK", "SUCCESS", "Use mild weather plan", List.of(), null),
                        List.of(),
                        List.of()
                ));
        when(reviewer.review(any(ReviewTeamCommand.class))).thenReturn(new TeamReviewResultDTO(review, List.of()));

        return new TeamGraphSupport(
                contextBuilder,
                agentToolResolver,
                planner,
                executor,
                reviewer,
                new TeamAnswerDraftBuilder(),
                new TaskPlanValidator(),
                new TaskDependencySorter(),
                traceService,
                tokenUsageService,
                objectMapper
        );
    }

    private TeamGraphSupport supportForRetryLoop(TaskPlanDTO plan) {
        AgentContextBuilder contextBuilder = mock(AgentContextBuilder.class);
        AgentToolResolver agentToolResolver = mock(AgentToolResolver.class);
        TeamPlanner planner = mock(TeamPlanner.class);
        TeamExecutor executor = mock(TeamExecutor.class);
        TeamReviewer reviewer = mock(TeamReviewer.class);
        TraceService traceService = mock(TraceService.class);
        TokenUsageService tokenUsageService = mock(TokenUsageService.class);

        AgentContextDTO context = context();
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context);
        when(agentToolResolver.resolve(context)).thenReturn(List.of(tool("weather")));
        when(planner.plan(any(PlanTeamCommand.class))).thenReturn(new TeamPlanResultDTO(plan, List.of()));
        when(executor.execute(any(ExecuteTeamTaskCommand.class)))
                .thenReturn(new TeamTaskExecutionResultDTO(
                        new ExecutionResultDTO("task-1", "TOOL_TASK", "SUCCESS", "Weather is mild", List.of("weather"), null),
                        List.of(),
                        List.of()
                ))
                .thenReturn(new TeamTaskExecutionResultDTO(
                        new ExecutionResultDTO("task-2", "MODEL_TASK", "SUCCESS", "Use mild weather plan", List.of(), null),
                        List.of(),
                        List.of()
                ))
                .thenReturn(new TeamTaskExecutionResultDTO(
                        new ExecutionResultDTO("task-1", "TOOL_TASK", "SUCCESS", "Retry weather result", List.of("weather"), null),
                        List.of(),
                        List.of()
                ));
        when(reviewer.review(any(ReviewTeamCommand.class)))
                .thenReturn(new TeamReviewResultDTO(
                        new ReviewResultDTO(false, List.of(), List.of("task-1"), "retry task-1"),
                        List.of()
                ))
                .thenReturn(new TeamReviewResultDTO(
                        new ReviewResultDTO(true, List.of(), List.of(), "review passed"),
                        List.of()
                ));

        return support(contextBuilder, agentToolResolver, planner, executor, reviewer, traceService, tokenUsageService);
    }

    private TeamGraphSupport supportForReplanLoop(TaskPlanDTO plan, TaskPlanDTO replanned) {
        AgentContextBuilder contextBuilder = mock(AgentContextBuilder.class);
        AgentToolResolver agentToolResolver = mock(AgentToolResolver.class);
        TeamPlanner planner = mock(TeamPlanner.class);
        TeamExecutor executor = mock(TeamExecutor.class);
        TeamReviewer reviewer = mock(TeamReviewer.class);
        TraceService traceService = mock(TraceService.class);
        TokenUsageService tokenUsageService = mock(TokenUsageService.class);

        AgentContextDTO context = context();
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context);
        when(agentToolResolver.resolve(context)).thenReturn(List.of(tool("weather")));
        when(planner.plan(any(PlanTeamCommand.class)))
                .thenReturn(new TeamPlanResultDTO(plan, List.of()))
                .thenReturn(new TeamPlanResultDTO(replanned, List.of()));
        when(executor.execute(any(ExecuteTeamTaskCommand.class)))
                .thenReturn(new TeamTaskExecutionResultDTO(
                        new ExecutionResultDTO("task-1", "TOOL_TASK", "SUCCESS", "Weather is mild", List.of("weather"), null),
                        List.of(),
                        List.of()
                ))
                .thenReturn(new TeamTaskExecutionResultDTO(
                        new ExecutionResultDTO("task-2", "MODEL_TASK", "SUCCESS", "Use mild weather plan", List.of(), null),
                        List.of(),
                        List.of()
                ))
                .thenReturn(new TeamTaskExecutionResultDTO(
                        new ExecutionResultDTO("task-3", "MODEL_TASK", "SUCCESS", "Budget is 200", List.of(), null),
                        List.of(),
                        List.of()
                ));
        when(reviewer.review(any(ReviewTeamCommand.class)))
                .thenReturn(new TeamReviewResultDTO(
                        new ReviewResultDTO(
                                false,
                                List.of(new ReviewResultDTO.ReviewIssueDTO(null, "WARN", "Need budget")),
                                List.of(),
                                "replan required",
                                true,
                                "Add budget task"
                        ),
                        List.of()
                ))
                .thenReturn(new TeamReviewResultDTO(
                        new ReviewResultDTO(true, List.of(), List.of(), "review passed"),
                        List.of()
                ));

        return support(contextBuilder, agentToolResolver, planner, executor, reviewer, traceService, tokenUsageService);
    }

    private TeamGraphSupport support(
            AgentContextBuilder contextBuilder,
            AgentToolResolver agentToolResolver,
            TeamPlanner planner,
            TeamExecutor executor,
            TeamReviewer reviewer,
            TraceService traceService,
            TokenUsageService tokenUsageService
    ) {
        return new TeamGraphSupport(
                contextBuilder,
                agentToolResolver,
                planner,
                executor,
                reviewer,
                new TeamAnswerDraftBuilder(),
                new TaskPlanValidator(),
                new TaskDependencySorter(),
                traceService,
                tokenUsageService,
                objectMapper
        );
    }
}
