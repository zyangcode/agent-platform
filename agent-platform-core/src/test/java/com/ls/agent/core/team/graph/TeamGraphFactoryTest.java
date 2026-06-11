package com.ls.agent.core.team.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.agent.command.AgentRunCommand;
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
import com.ls.agent.core.team.api.TeamPlanner;
import com.ls.agent.core.team.application.TaskDependencySorter;
import com.ls.agent.core.team.application.TaskPlanValidator;
import com.ls.agent.core.team.application.TeamRunLimiter;
import com.ls.agent.core.team.command.PlanTeamCommand;
import com.ls.agent.core.team.dto.TaskPlanDTO;
import com.ls.agent.core.team.dto.TeamPlanResultDTO;
import com.ls.agent.core.team.dto.TeamRuntimeEventDTO;
import com.ls.agent.core.team.dto.TeamTaskDTO;
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
    void runsBuildContextPlanValidateAndScheduleNodes() {
        AgentContextBuilder contextBuilder = mock(AgentContextBuilder.class);
        AgentToolResolver agentToolResolver = mock(AgentToolResolver.class);
        TeamPlanner planner = mock(TeamPlanner.class);
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

        TeamGraphSupport support = new TeamGraphSupport(
                contextBuilder,
                agentToolResolver,
                planner,
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
        assertThat(finalState.step()).isEqualTo(2);
        assertThat(finalState.route()).isEqualTo(TeamGraphRoute.FINAL);

        verify(contextBuilder).build(any(BuildAgentContextCommand.class));
        verify(agentToolResolver).resolve(context);
        verify(planner).plan(any(PlanTeamCommand.class));
        verify(tokenUsageService).record(any(RecordTokenUsageCommand.class));

        ArgumentCaptor<TeamRuntimeEventDTO> eventCaptor = ArgumentCaptor.forClass(TeamRuntimeEventDTO.class);
        verify(eventSink, times(1)).emit(eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo(TeamRuntimeEventDTO.TYPE_TEAM_PLAN);
        assertThat(eventCaptor.getValue().step()).isEqualTo(1);
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
}
