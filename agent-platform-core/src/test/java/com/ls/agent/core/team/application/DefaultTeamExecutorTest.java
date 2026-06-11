package com.ls.agent.core.team.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.agent.tool.AgentToolDTO;
import com.ls.agent.core.agent.tool.AgentToolDispatchCommand;
import com.ls.agent.core.agent.tool.AgentToolDispatchResult;
import com.ls.agent.core.agent.tool.AgentToolDispatcher;
import com.ls.agent.core.agent.tool.AgentToolRiskLevel;
import com.ls.agent.core.agent.tool.AgentToolSourceType;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.model.dto.ModelToolCallDTO;
import com.ls.agent.core.model.dto.ModelToolSpecDTO;
import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.team.command.ExecuteTeamTaskCommand;
import com.ls.agent.core.team.dto.ExecutionResultDTO;
import com.ls.agent.core.team.dto.TeamTaskDTO;
import com.ls.agent.core.team.dto.TeamTaskExecutionResultDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultTeamExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentToolDispatcher toolDispatcher = mock(AgentToolDispatcher.class);
    private final ModelInvokeService modelInvokeService = mock(ModelInvokeService.class);
    private final DefaultTeamExecutor executor = new DefaultTeamExecutor(toolDispatcher, modelInvokeService);

    @Test
    void executesAuthorizedToolTask() {
        when(toolDispatcher.dispatch(any(AgentToolDispatchCommand.class))).thenReturn(new AgentToolDispatchResult(
                true,
                "weather",
                AgentToolSourceType.SKILL,
                objectMapper.createObjectNode().put("forecast", "sunny"),
                null
        ));

        TeamTaskExecutionResultDTO result = executor.execute(command(
                toolTask("task-1", "weather"),
                List.of(tool("weather", AgentToolSourceType.SKILL)),
                List.of()
        ));

        assertThat(result.executionResult().status()).isEqualTo("SUCCESS");
        assertThat(result.executionResult().result()).contains("sunny");
        assertThat(result.executionResult().usedTools()).containsExactly("weather");
        assertThat(result.toolResults()).hasSize(1);
        assertThat(result.modelInvocations()).isEmpty();

        ArgumentCaptor<AgentToolDispatchCommand> captor = ArgumentCaptor.forClass(AgentToolDispatchCommand.class);
        verify(toolDispatcher).dispatch(captor.capture());
        assertThat(captor.getValue().toolName()).isEqualTo("weather");
        assertThat(captor.getValue().sourceType()).isEqualTo(AgentToolSourceType.SKILL);
    }

    @Test
    void rejectsUnauthorizedToolWithoutDispatching() {
        TeamTaskExecutionResultDTO result = executor.execute(command(
                toolTask("task-1", "search"),
                List.of(tool("weather", AgentToolSourceType.SKILL)),
                List.of()
        ));

        assertThat(result.executionResult().status()).isEqualTo("FAILED");
        assertThat(result.executionResult().errorMessage()).contains("not authorized");
        assertThat(result.toolResults()).isEmpty();
        verifyNoInteractions(toolDispatcher);
    }

    @Test
    void returnsFailedWhenToolDispatcherFails() {
        when(toolDispatcher.dispatch(any(AgentToolDispatchCommand.class))).thenReturn(new AgentToolDispatchResult(
                false,
                "weather",
                AgentToolSourceType.SKILL,
                objectMapper.createObjectNode(),
                "tool down"
        ));

        TeamTaskExecutionResultDTO result = executor.execute(command(
                toolTask("task-1", "weather"),
                List.of(tool("weather", AgentToolSourceType.SKILL)),
                List.of()
        ));

        assertThat(result.executionResult().status()).isEqualTo("FAILED");
        assertThat(result.executionResult().errorMessage()).isEqualTo("tool down");
        assertThat(result.toolResults()).hasSize(1);
    }

    @Test
    void executesModelTaskFromFunctionCallArguments() {
        com.fasterxml.jackson.databind.node.ObjectNode arguments = objectMapper.createObjectNode()
                .put("taskId", "task-2")
                .put("taskType", "MODEL_TASK")
                .put("status", "SUCCESS")
                .put("result", "Structured model task result")
                .putNull("errorMessage");
        arguments.set("usedTools", objectMapper.createArrayNode());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult(
                "",
                new ModelToolCallDTO("TEAM", "team_model_task_result", arguments)
        ));

        TeamTaskExecutionResultDTO result = executor.execute(command(
                modelTask("task-2", List.of("task-1")),
                List.of(),
                List.of(new ExecutionResultDTO("task-1", "TOOL_TASK", "SUCCESS", "Weather sunny", List.of("weather"), null))
        ));

        assertThat(result.executionResult().status()).isEqualTo("SUCCESS");
        assertThat(result.executionResult().result()).isEqualTo("Structured model task result");
        assertThat(result.executionResult().taskId()).isEqualTo("task-2");
        assertThat(result.executionResult().taskType()).isEqualTo("MODEL_TASK");
        assertThat(result.executionResult().usedTools()).isEmpty();

        ArgumentCaptor<ModelInvokeCommand> captor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService).invoke(captor.capture());
        assertThat(captor.getValue().tools()).extracting(ModelToolSpecDTO::name)
                .containsExactly("team_model_task_result");
    }

    @Test
    void executesModelTaskWithPreviousResultsInPrompt() {
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("Model task result"));

        TeamTaskExecutionResultDTO result = executor.execute(command(
                modelTask("task-2", List.of("task-1")),
                List.of(),
                List.of(new ExecutionResultDTO("task-1", "TOOL_TASK", "SUCCESS", "Weather sunny", List.of("weather"), null))
        ));

        assertThat(result.executionResult().status()).isEqualTo("SUCCESS");
        assertThat(result.executionResult().result()).isEqualTo("Model task result");
        assertThat(result.modelInvocations()).hasSize(1);
        assertThat(result.toolResults()).isEmpty();

        ArgumentCaptor<ModelInvokeCommand> captor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService).invoke(captor.capture());
        assertThat(captor.getValue().modelConfigId()).isEqualTo(30001L);
        assertThat(captor.getValue().stream()).isFalse();
        assertThat(captor.getValue().messages()).extracting(ModelMessage::role).containsExactly("system", "user");
        String prompt = captor.getValue().messages().get(1).content();
        assertThat(prompt).contains("Original user request");
        assertThat(prompt).contains("Current task");
        assertThat(prompt).contains("Weather sunny");
        assertThat(prompt).contains("Keep execution concise.");
    }

    @Test
    void returnsFailedWhenModelTaskThrows() {
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenThrow(new IllegalStateException("model down"));

        TeamTaskExecutionResultDTO result = executor.execute(command(
                modelTask("task-1", List.of()),
                List.of(),
                List.of()
        ));

        assertThat(result.executionResult().status()).isEqualTo("FAILED");
        assertThat(result.executionResult().errorMessage()).contains("model down");
        assertThat(result.modelInvocations()).isEmpty();
    }

    @Test
    void skipsTaskWhenDependencyFailed() {
        TeamTaskExecutionResultDTO result = executor.execute(command(
                modelTask("task-2", List.of("task-1")),
                List.of(),
                List.of(new ExecutionResultDTO("task-1", "TOOL_TASK", "FAILED", "", List.of(), "tool down"))
        ));

        assertThat(result.executionResult().status()).isEqualTo("SKIPPED");
        assertThat(result.executionResult().errorMessage()).contains("dependsOn task task-1 is not successful");
        verifyNoInteractions(modelInvokeService);
        verifyNoInteractions(toolDispatcher);
    }

    private ExecuteTeamTaskCommand command(
            TeamTaskDTO task,
            List<AgentToolDTO> tools,
            List<ExecutionResultDTO> previousResults
    ) {
        return new ExecuteTeamTaskCommand(
                1L,
                10001L,
                "Plan a light team activity",
                task,
                context(),
                tools,
                previousResults
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
                        "Keep execution concise.",
                        objectMapper.createObjectNode(),
                        5,
                        "TEAM",
                        "PRIVATE",
                        "DRAFT",
                        List.of(),
                        List.of()
                ),
                List.of(),
                List.of(),
                List.of(),
                120,
                false
        );
    }

    private TeamTaskDTO toolTask(String id, String suggestedTool) {
        return new TeamTaskDTO(
                id,
                "Check weather",
                "Check current weather.",
                "TOOL_TASK",
                suggestedTool,
                objectMapper.createObjectNode().put("city", "Chongqing"),
                List.of()
        );
    }

    private TeamTaskDTO modelTask(String id, List<String> dependsOn) {
        return new TeamTaskDTO(
                id,
                "Summarize",
                "Summarize the previous findings.",
                "MODEL_TASK",
                null,
                objectMapper.createObjectNode(),
                dependsOn
        );
    }

    private AgentToolDTO tool(String name, AgentToolSourceType sourceType) {
        return new AgentToolDTO(
                name,
                name,
                "Demo tool",
                sourceType,
                objectMapper.createObjectNode(),
                AgentToolRiskLevel.LOW
        );
    }

    private ModelInvokeResult modelResult(String content) {
        return new ModelInvokeResult(30001L, 1L, "mock", "mock-chat", content, new ModelUsageDTO(3, 4, 7, true));
    }

    private ModelInvokeResult modelResult(String content, ModelToolCallDTO toolCall) {
        return new ModelInvokeResult(
                30001L,
                1L,
                "mock",
                "mock-chat",
                content,
                new ModelUsageDTO(3, 4, 7, true),
                List.of(toolCall)
        );
    }
}
