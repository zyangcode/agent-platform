package com.ls.agent.core.agent;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.api.ConversationRepository;
import com.ls.agent.core.agent.application.DefaultAgentRuntimeService;
import com.ls.agent.core.agent.application.SingleAgentFinalResponseSynthesizer;
import com.ls.agent.core.agent.hook.AgentRuntimeHook;
import com.ls.agent.core.agent.hook.FinalAnswerHookContext;
import com.ls.agent.core.agent.hook.ModelHookContext;
import com.ls.agent.core.agent.hook.ToolHookContext;
import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.command.PendingToolCallCommand;
import com.ls.agent.core.agent.dto.AgentRunResult;
import com.ls.agent.core.agent.tool.AgentToolDTO;
import com.ls.agent.core.agent.tool.AgentToolDispatchCommand;
import com.ls.agent.core.agent.tool.AgentToolDispatchResult;
import com.ls.agent.core.agent.tool.AgentToolDispatcher;
import com.ls.agent.core.agent.tool.AgentToolResolver;
import com.ls.agent.core.agent.tool.AgentToolRiskLevel;
import com.ls.agent.core.agent.tool.AgentToolSourceType;
import com.ls.agent.core.agent.tool.DefaultAgentToolCallValidator;
import com.ls.agent.core.agent.tool.DefaultToolExecutionPlanner;
import com.ls.agent.core.agent.entity.ConversationEntity;
import com.ls.agent.core.context.api.AgentContextBuilder;
import com.ls.agent.core.context.api.MicroCompactService;
import com.ls.agent.core.context.application.DefaultMicroCompactService;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.context.dto.MicroCompactResult;
import com.ls.agent.core.memory.application.PreferenceExtractor;
import com.ls.agent.core.mcp.api.McpToolExecutor;
import com.ls.agent.core.mcp.dto.McpToolExecuteResult;
import com.ls.agent.core.memory.api.MemoryWriteService;
import com.ls.agent.core.memory.command.RecordMemoryCommand;
import com.ls.agent.core.model.api.ModelStreamCallback;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.model.dto.ModelToolCallDTO;
import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.skill.api.SkillExecutor;
import com.ls.agent.core.skill.command.SkillExecuteCommand;
import com.ls.agent.core.skill.dto.SkillDTO;
import com.ls.agent.core.skill.dto.SkillExecuteResult;
import com.ls.agent.core.mcp.dto.McpToolDTO;
import com.ls.agent.core.team.api.TeamEventSink;
import com.ls.agent.core.team.api.TeamRuntimeService;
import com.ls.agent.core.quota.api.TokenUsageService;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.FinishTraceSpanCommand;
import com.ls.agent.core.quota.command.RecordTokenUsageCommand;
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceSpanDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAgentRuntimeServiceTest {

    private final AgentContextBuilder contextBuilder = mock(AgentContextBuilder.class);
    private final ModelInvokeService modelInvokeService = mock(ModelInvokeService.class);
    private final SkillExecutor skillExecutor = mock(SkillExecutor.class);
    private final McpToolExecutor mcpToolExecutor = mock(McpToolExecutor.class);
    private final AgentToolResolver agentToolResolver = mock(AgentToolResolver.class);
    private final AgentToolDispatcher agentToolDispatcher = mock(AgentToolDispatcher.class);
    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final MemoryWriteService memoryWriteService = mock(MemoryWriteService.class);
    private final TraceService traceService = mock(TraceService.class);
    private final TokenUsageService tokenUsageService = mock(TokenUsageService.class);
    private final TeamRuntimeService teamRuntimeService = mock(TeamRuntimeService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultAgentToolCallValidator agentToolCallValidator = new DefaultAgentToolCallValidator(objectMapper);
    private final DefaultToolExecutionPlanner toolExecutionPlanner = new DefaultToolExecutionPlanner();
    private final DefaultMicroCompactService microCompactService = new DefaultMicroCompactService();
    private final SingleAgentFinalResponseSynthesizer finalResponseSynthesizer = new SingleAgentFinalResponseSynthesizer();
    private final PreferenceExtractor preferenceExtractor = new PreferenceExtractor();
    private final DefaultAgentRuntimeService service = new DefaultAgentRuntimeService(
            contextBuilder,
            microCompactService,
            modelInvokeService,
            agentToolResolver,
            agentToolDispatcher,
            agentToolCallValidator,
            toolExecutionPlanner,
            teamRuntimeService,
            finalResponseSynthesizer,
            conversationRepository,
            memoryWriteService,
            preferenceExtractor,
            traceService,
            tokenUsageService,
            objectMapper,
            List.of()
    );

    @Test
    void runCreatesConversationInvokesModelAndStoresMessages() {
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("done"));
        when(conversationRepository.findConversationById(90001L)).thenReturn(null);
        doAnswer(invocation -> {
            ConversationEntity entity = invocation.getArgument(0);
            entity.setId(90001L);
            return null;
        }).when(conversationRepository).insertConversation(any());

        AgentRunResult result = service.run(command(null));

        assertThat(result.conversationId()).isEqualTo(90001L);
        assertThat(result.assistantMessage()).isEqualTo("done");
        assertThat(result.usage().totalTokens()).isEqualTo(4);

        ArgumentCaptor<com.ls.agent.core.agent.entity.ConversationMessageEntity> messageCaptor =
                ArgumentCaptor.forClass(com.ls.agent.core.agent.entity.ConversationMessageEntity.class);
        verify(conversationRepository, times(2)).insertMessage(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues()).extracting("role")
                .containsExactly("user", "assistant");
        assertThat(messageCaptor.getAllValues()).extracting("content")
                .containsExactly("hello", "done");

        ArgumentCaptor<RecordMemoryCommand> memoryCaptor = ArgumentCaptor.forClass(RecordMemoryCommand.class);
        verify(memoryWriteService).record(memoryCaptor.capture());
        assertThat(memoryCaptor.getValue().content()).contains("hello", "done");
        assertThat(memoryCaptor.getValue().memoryStrategyMode()).isEqualTo("READ_WRITE");
        assertThat(memoryCaptor.getValue().memoryScope()).isEqualTo("CONVERSATION_TEMP");
    }

    @Test
    void runStoresExtractedPreferenceMemoriesAlongsideSummary() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("记住了。"));

        service.run(commandWithInput(90001L, "我喜欢傍晚打篮球"));

        ArgumentCaptor<RecordMemoryCommand> memoryCaptor = ArgumentCaptor.forClass(RecordMemoryCommand.class);
        verify(memoryWriteService, times(2)).record(memoryCaptor.capture());
        assertThat(memoryCaptor.getAllValues()).extracting(RecordMemoryCommand::memoryType)
                .containsExactly("SUMMARY", "PREFERENCE");
        assertThat(memoryCaptor.getAllValues().get(1).content()).isEqualTo("用户偏好：傍晚打篮球");
        assertThat(memoryCaptor.getAllValues().get(1).memoryCategory()).isEqualTo("preference");
        assertThat(memoryCaptor.getAllValues().get(1).tags()).containsExactly("preference", "positive");
        assertThat(memoryCaptor.getAllValues()).extracting(RecordMemoryCommand::memoryStrategyMode)
                .containsExactly("READ_WRITE", "READ_WRITE");
        assertThat(memoryCaptor.getAllValues()).extracting(RecordMemoryCommand::memoryScope)
                .containsExactly("CONVERSATION_TEMP", "PROFILE_LONG_TERM");
    }

    @Test
    void runSkipsPersistentMemoryWriteWhenProfileMemoryStrategyIsReadOnly() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class)))
                .thenReturn(contextWithMemoryStrategy("READ_ONLY"));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("done"));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage()).isEqualTo("done");
        verify(memoryWriteService, never()).record(any());
    }

    @Test
    void runSkipsPersistentMemoryWriteWhenProfileMemoryStrategyIsDisabled() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class)))
                .thenReturn(contextWithMemoryStrategy("DISABLED"));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("done"));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage()).isEqualTo("done");
        verify(memoryWriteService, never()).record(any());
    }

    @Test
    void runSkipsPersistentMemoryWriteWhenProfileMemoryStrategyIsSessionOnly() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class)))
                .thenReturn(contextWithMemoryStrategy("SESSION_ONLY"));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("done"));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage()).isEqualTo("done");
        verify(memoryWriteService, never()).record(any());
    }

    @Test
    void runWritesPersistentMemoryWhenProfileMemoryStrategyIsReadWrite() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class)))
                .thenReturn(contextWithMemoryStrategy("READ_WRITE"));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("done"));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage()).isEqualTo("done");
        verify(memoryWriteService).record(any(RecordMemoryCommand.class));
    }

    @Test
    void runStreamsOnlyFinalAssistantMessageToCallbackAndStillPersistsFullAssistantMessage() {
        List<String> tokens = new ArrayList<>();
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("hello"));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class), any(ModelStreamCallback.class)))
                .thenAnswer(invocation -> {
                    ModelStreamCallback callback = invocation.getArgument(1);
                    callback.onToken("hel");
                    callback.onToken("lo");
                    return modelResult("hello");
                });

        AgentRunResult result = service.run(command(90001L), null, tokens::add);

        assertThat(tokens).containsExactly("hel", "lo");
        assertThat(result.assistantMessage()).isEqualTo("hello");
        ArgumentCaptor<ModelInvokeCommand> modelCaptor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService).invoke(modelCaptor.capture(), any(ModelStreamCallback.class));
        assertThat(modelCaptor.getValue().stream()).isFalse();
        ArgumentCaptor<com.ls.agent.core.agent.entity.ConversationMessageEntity> messageCaptor =
                ArgumentCaptor.forClass(com.ls.agent.core.agent.entity.ConversationMessageEntity.class);
        verify(conversationRepository, times(2)).insertMessage(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues()).extracting("content")
                .containsExactly("hello", "hello");
    }

    @Test
    void runDoesNotStreamInternalToolCallTokensToUserCallback() {
        List<String> tokens = new ArrayList<>();
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(agentTool("calculator", AgentToolSourceType.SKILL)));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@skill:calculator {\"expression\":\"1+2\"}"))
                .thenReturn(modelResult("The result is 3."));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class), any(ModelStreamCallback.class)))
                .thenAnswer(invocation -> {
                    ModelStreamCallback callback = invocation.getArgument(1);
                    callback.onToken("The result ");
                    callback.onToken("is 3.");
                    return modelResult("The result is 3.");
                });
        when(agentToolDispatcher.dispatch(any())).thenReturn(new AgentToolDispatchResult(
                true,
                "calculator",
                AgentToolSourceType.SKILL,
                objectMapper.createObjectNode().put("result", "3"),
                null
        ));

        AgentRunResult result = service.run(command(90001L), null, tokens::add);

        assertThat(result.assistantMessage()).isEqualTo("The result is 3.");
        assertThat(tokens).containsExactly("The result ", "is 3.");
        assertThat(String.join("", tokens)).doesNotContain("@skill:", "expression");
    }

    @Test
    void runRepairsWeatherIntentWhenModelPromisesLookupWithoutToolCall() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithWeatherSkill());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(agentTool("weather", AgentToolSourceType.SKILL)));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("好的，马上查询重庆天气！"))
                .thenReturn(modelResult("重庆当前多云，气温适中，适合打篮球。"));
        when(agentToolDispatcher.dispatch(any())).thenReturn(new AgentToolDispatchResult(
                true,
                "weather",
                AgentToolSourceType.SKILL,
                objectMapper.createObjectNode()
                        .put("city", "重庆")
                        .put("summary", "重庆 current weather: cloudy, 24C"),
                null
        ));

        AgentRunResult result = service.run(commandWithInput(90001L, "重庆"));

        assertThat(result.assistantMessage()).isEqualTo("重庆当前多云，气温适中，适合打篮球。");
        ArgumentCaptor<AgentToolDispatchCommand> dispatchCaptor = ArgumentCaptor.forClass(AgentToolDispatchCommand.class);
        verify(agentToolDispatcher).dispatch(dispatchCaptor.capture());
        assertThat(dispatchCaptor.getValue().toolName()).isEqualTo("weather");
        assertThat(dispatchCaptor.getValue().arguments().get("city").asText()).isEqualTo("重庆");
        assertThat(result.toolEvents()).extracting("type", "toolName")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("action", "weather"),
                        org.assertj.core.groups.Tuple.tuple("observation", "weather")
        );
        verify(modelInvokeService, times(2)).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void runStoresReflectionMemoryAfterSuccessfulToolExecution() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithWeatherSkill());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(agentTool("weather", AgentToolSourceType.SKILL)));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@skill:weather {\"city\":\"Chongqing\"}"))
                .thenReturn(modelResult("Chongqing is cloudy."));
        when(agentToolDispatcher.dispatch(any())).thenReturn(new AgentToolDispatchResult(
                true,
                "weather",
                AgentToolSourceType.SKILL,
                objectMapper.createObjectNode()
                        .put("city", "Chongqing")
                        .put("summary", "cloudy"),
                null
        ));

        service.run(commandWithInput(90001L, "Check Chongqing weather"));

        ArgumentCaptor<RecordMemoryCommand> memoryCaptor = ArgumentCaptor.forClass(RecordMemoryCommand.class);
        verify(memoryWriteService, times(2)).record(memoryCaptor.capture());
        assertThat(memoryCaptor.getAllValues()).extracting(RecordMemoryCommand::memoryType)
                .containsExactly("SUMMARY", "REFLECTION");
        RecordMemoryCommand reflection = memoryCaptor.getAllValues().get(1);
        assertThat(reflection.memoryCategory()).isEqualTo("reflection");
        assertThat(reflection.tags()).containsExactly("reflection", "tool_success", "tool:weather");
        assertThat(reflection.content())
                .contains("Tool execution succeeded")
                .contains("weather")
                .contains("Check Chongqing weather")
                .contains("Chongqing is cloudy.");
        assertThat(reflection.memoryStrategyMode()).isEqualTo("READ_WRITE");
    }

    @Test
    void runStoresToolFailureMemoryAfterFailedToolExecution() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithWeatherSkill());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(agentTool("weather", AgentToolSourceType.SKILL)));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@skill:weather {\"city\":\"Chongqing\"}"))
                .thenReturn(modelResult("I could not fetch the weather."));
        when(agentToolDispatcher.dispatch(any())).thenReturn(new AgentToolDispatchResult(
                false,
                "weather",
                AgentToolSourceType.SKILL,
                null,
                "weather api timeout"
        ));

        service.run(commandWithInput(90001L, "Check Chongqing weather"));

        ArgumentCaptor<RecordMemoryCommand> memoryCaptor = ArgumentCaptor.forClass(RecordMemoryCommand.class);
        verify(memoryWriteService, times(3)).record(memoryCaptor.capture());
        assertThat(memoryCaptor.getAllValues()).extracting(RecordMemoryCommand::memoryType)
                .containsExactly("SUMMARY", "TOOL_FAILURE", "REFLECTION");
        RecordMemoryCommand toolFailure = memoryCaptor.getAllValues().get(1);
        assertThat(toolFailure.memoryCategory()).isEqualTo("tool_failure");
        assertThat(toolFailure.tags()).containsExactly("tool_failure", "tool:weather");
        assertThat(toolFailure.content())
                .contains("Tool execution failed")
                .contains("weather")
                .contains("api timeout");
        assertThat(toolFailure.memoryStrategyMode()).isEqualTo("READ_WRITE");
    }

    @Test
    void runRepairsWeatherIntentWithMcpWeatherToolWhenModelSkipsFunctionCall() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithWeatherMcpTool());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(agentTool("weather.current", AgentToolSourceType.MCP)));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("我可以帮你查天气，告诉我城市名就行。"))
                .thenReturn(modelResult("重庆当前多云偏闷热，建议傍晚打篮球。"));
        when(agentToolDispatcher.dispatch(any())).thenReturn(new AgentToolDispatchResult(
                true,
                "weather.current",
                AgentToolSourceType.MCP,
                objectMapper.createObjectNode()
                        .put("city", "重庆")
                        .put("summary", "重庆当前多云偏闷热，约31℃，适合傍晚打篮球。"),
                null
        ));

        AgentRunResult result = service.run(commandWithInput(90001L, "重庆今天适合打篮球吗？请查天气后回答"));

        assertThat(result.assistantMessage()).isEqualTo("重庆当前多云偏闷热，建议傍晚打篮球。");
        ArgumentCaptor<AgentToolDispatchCommand> dispatchCaptor = ArgumentCaptor.forClass(AgentToolDispatchCommand.class);
        verify(agentToolDispatcher).dispatch(dispatchCaptor.capture());
        assertThat(dispatchCaptor.getValue().sourceType()).isEqualTo(AgentToolSourceType.MCP);
        assertThat(dispatchCaptor.getValue().toolName()).isEqualTo("weather.current");
        assertThat(dispatchCaptor.getValue().arguments().get("city").asText()).isEqualTo("重庆");
        assertThat(result.toolEvents()).extracting("type", "toolName")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("action", "weather.current"),
                        org.assertj.core.groups.Tuple.tuple("observation", "weather.current")
                );
        verify(modelInvokeService, times(2)).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void runRepairsExplicitWeatherRequestWithMcpWeatherToolEvenWhenAssistantDoesNotPromiseLookup() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithWeatherMcpTool());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(agentTool("weather.current", AgentToolSourceType.MCP)));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("你又来了问号攻击。"))
                .thenReturn(modelResult("重庆当前多云偏闷热，建议傍晚打篮球。"));
        when(agentToolDispatcher.dispatch(any())).thenReturn(new AgentToolDispatchResult(
                true,
                "weather.current",
                AgentToolSourceType.MCP,
                objectMapper.createObjectNode()
                        .put("city", "重庆")
                        .put("summary", "重庆当前多云偏闷热，约31℃，适合傍晚打篮球。"),
                null
        ));

        AgentRunResult result = service.run(commandWithInput(90001L, "重庆今天适合打篮球吗？请查天气后回答"));

        assertThat(result.assistantMessage()).isEqualTo("重庆当前多云偏闷热，建议傍晚打篮球。");
        ArgumentCaptor<AgentToolDispatchCommand> dispatchCaptor = ArgumentCaptor.forClass(AgentToolDispatchCommand.class);
        verify(agentToolDispatcher).dispatch(dispatchCaptor.capture());
        assertThat(dispatchCaptor.getValue().sourceType()).isEqualTo(AgentToolSourceType.MCP);
        assertThat(dispatchCaptor.getValue().toolName()).isEqualTo("weather.current");
        assertThat(dispatchCaptor.getValue().arguments().get("city").asText()).isEqualTo("重庆");
        verify(modelInvokeService, times(2)).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void runExecutesStructuredFunctionToolCallBeforeFinalAnswer() {
        List<String> tokens = new ArrayList<>();
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithWeatherSkill());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(agentTool("weather", AgentToolSourceType.SKILL)));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class), any(ModelStreamCallback.class)))
                .thenReturn(modelResultWithToolCall("SKILL", "weather", objectMapper.createObjectNode().put("city", "重庆")))
                .thenReturn(modelResult("重庆今天适合打篮球。"));
        when(agentToolDispatcher.dispatch(any())).thenReturn(new AgentToolDispatchResult(
                true,
                "weather",
                AgentToolSourceType.SKILL,
                objectMapper.createObjectNode()
                        .put("city", "重庆")
                        .put("summary", "cloudy, 24C"),
                null
        ));

        AgentRunResult result = service.run(commandWithInput(90001L, "查重庆天气"), null, tokens::add);

        assertThat(result.assistantMessage()).isEqualTo("重庆今天适合打篮球。");
        assertThat(tokens).containsExactly("重庆今天适合打篮球。");
        ArgumentCaptor<AgentToolDispatchCommand> dispatchCaptor = ArgumentCaptor.forClass(AgentToolDispatchCommand.class);
        verify(agentToolDispatcher, times(1)).dispatch(dispatchCaptor.capture());
        assertThat(dispatchCaptor.getValue().toolName()).isEqualTo("weather");
        assertThat(dispatchCaptor.getValue().arguments().get("city").asText()).isEqualTo("重庆");

        assertThat(result.toolEvents()).extracting("type", "toolName")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("action", "weather"),
                        org.assertj.core.groups.Tuple.tuple("observation", "weather")
                );
    }

    @Test
    void runExecutesStructuredMcpFunctionToolCallBeforeFinalAnswer() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithWeatherMcpTool());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(agentTool("weather.current", AgentToolSourceType.MCP)));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResultWithToolCall("MCP", "weather.current", objectMapper.createObjectNode().put("city", "重庆")))
                .thenReturn(modelResult("重庆当前多云偏闷热，建议傍晚打篮球。"));
        when(agentToolDispatcher.dispatch(any())).thenReturn(new AgentToolDispatchResult(
                true,
                "weather.current",
                AgentToolSourceType.MCP,
                objectMapper.createObjectNode()
                        .put("city", "重庆")
                        .put("summary", "重庆当前多云偏闷热，约31℃，适合傍晚打篮球。"),
                null
        ));

        AgentRunResult result = service.run(commandWithInput(90001L, "重庆今天适合打篮球吗"));

        assertThat(result.assistantMessage()).isEqualTo("重庆当前多云偏闷热，建议傍晚打篮球。");
        ArgumentCaptor<AgentToolDispatchCommand> dispatchCaptor = ArgumentCaptor.forClass(AgentToolDispatchCommand.class);
        verify(agentToolDispatcher).dispatch(dispatchCaptor.capture());
        assertThat(dispatchCaptor.getValue().sourceType()).isEqualTo(AgentToolSourceType.MCP);
        assertThat(dispatchCaptor.getValue().toolName()).isEqualTo("weather.current");
        assertThat(dispatchCaptor.getValue().arguments().get("city").asText()).isEqualTo("重庆");

        verify(modelInvokeService, times(2)).invoke(any(ModelInvokeCommand.class));
        assertThat(result.toolEvents()).extracting("type", "toolName")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("action", "weather.current"),
                        org.assertj.core.groups.Tuple.tuple("observation", "weather.current")
                );
    }

    @Test
    void runInvokesLightweightHooksAroundModelToolAndFinalAnswer() {
        List<String> hookEvents = new ArrayList<>();
        AgentRuntimeHook hook = new AgentRuntimeHook() {
            @Override
            public void preModelCall(ModelHookContext context) {
                hookEvents.add("preModel:" + context.step() + ":" + context.toolSpecCount());
            }

            @Override
            public void postModelCall(ModelHookContext context, ModelInvokeResult result, Exception error) {
                hookEvents.add("postModel:" + context.step() + ":" + (result == null ? "error" : result.modelName()));
            }

            @Override
            public void preToolCall(ToolHookContext context) {
                hookEvents.add("preTool:" + context.toolName() + ":" + context.riskLevel());
            }

            @Override
            public void postToolCall(ToolHookContext context, String output, Exception error) {
                hookEvents.add("postTool:" + context.toolName() + ":" + (output == null ? 0 : output.length()));
            }

            @Override
            public void postFinalAnswer(FinalAnswerHookContext context) {
                hookEvents.add("postFinal:" + context.finalChars() + ":" + context.changed());
            }
        };
        DefaultAgentRuntimeService hookedService = runtimeService(microCompactService, List.of(hook));

        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(
                agentTool("calculator", AgentToolSourceType.SKILL, AgentToolRiskLevel.LOW, true, List.of("math"))
        ));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@skill:calculator {\"expression\":\"1+2\"}"))
                .thenReturn(modelResult("The result is 3."));
        when(agentToolDispatcher.dispatch(any())).thenReturn(new AgentToolDispatchResult(
                true,
                "calculator",
                AgentToolSourceType.SKILL,
                objectMapper.createObjectNode().put("result", "3"),
                null
        ));

        AgentRunResult result = hookedService.run(command(90001L));

        assertThat(result.assistantMessage()).isEqualTo("The result is 3.");
        assertThat(hookEvents).containsExactly(
                "preModel:1:1",
                "postModel:1:mock-chat",
                "preTool:calculator:LOW",
                "postTool:calculator:14",
                "preModel:2:1",
                "postModel:2:mock-chat",
                "postFinal:16:false"
        );
    }

    @Test
    void runInvokesModelWithApiMessagesButPersistsOnlyCleanConversationMessages() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithSeparatedMessages());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("done"));

        service.run(command(90001L));

        ArgumentCaptor<ModelInvokeCommand> modelCaptor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService).invoke(modelCaptor.capture());
        assertThat(modelCaptor.getValue().messages().get(0).content())
                .contains("[memory] Ada likes concise answers.", "[rag] private reference");

        ArgumentCaptor<com.ls.agent.core.agent.entity.ConversationMessageEntity> messageCaptor =
                ArgumentCaptor.forClass(com.ls.agent.core.agent.entity.ConversationMessageEntity.class);
        verify(conversationRepository, times(2)).insertMessage(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues()).extracting("content")
                .containsExactly("hello", "done");
        assertThat(messageCaptor.getAllValues()).extracting("content")
                .allSatisfy(content -> assertThat(String.valueOf(content))
                        .doesNotContain("[memory]")
                        .doesNotContain("[rag]")
                        .doesNotContain("Tool specs"));
    }

    @Test
    void runDelegatesToTeamRuntimeWhenProfileExecutionModeIsTeam() {
        AgentRunResult teamResult = new AgentRunResult(90001L, "team answer", new ModelUsageDTO(1, 1, 2, true));
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithExecutionMode("TEAM"));
        when(teamRuntimeService.run(any(AgentRunCommand.class), isNull())).thenReturn(teamResult);

        AgentRunResult result = service.run(command(90001L));

        assertThat(result).isSameAs(teamResult);
        verify(teamRuntimeService).run(command(90001L), null);
        verify(modelInvokeService, never()).invoke(any(ModelInvokeCommand.class));
        verify(skillExecutor, never()).execute(any(SkillExecuteCommand.class));
        verify(mcpToolExecutor, never()).execute(any());

        ArgumentCaptor<com.ls.agent.core.agent.entity.ConversationMessageEntity> messageCaptor =
                ArgumentCaptor.forClass(com.ls.agent.core.agent.entity.ConversationMessageEntity.class);
        verify(conversationRepository, times(2)).insertMessage(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues()).extracting("role").containsExactly("user", "assistant");
        assertThat(messageCaptor.getAllValues()).extracting("content").containsExactly("hello", "team answer");
        verify(memoryWriteService).record(any(RecordMemoryCommand.class));
    }

    @Test
    void runDelegatesToTeamRuntimeWhenRequestAgentModeIsTeam() {
        AgentRunResult teamResult = new AgentRunResult(90001L, "team answer", new ModelUsageDTO(1, 1, 2, true));
        AgentRunCommand command = command(90001L, "team");
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithExecutionMode("BASIC"));
        when(teamRuntimeService.run(any(AgentRunCommand.class), isNull())).thenReturn(teamResult);

        AgentRunResult result = service.run(command);

        assertThat(result).isSameAs(teamResult);
        verify(teamRuntimeService).run(command, null);
        verify(modelInvokeService, never()).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void runPassesRequestScopedTeamEventSinkToTeamRuntime() {
        TeamEventSink teamEventSink = mock(TeamEventSink.class);
        AgentRunResult teamResult = new AgentRunResult(90001L, "team answer", new ModelUsageDTO(1, 1, 2, true));
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithExecutionMode("TEAM"));
        when(teamRuntimeService.run(any(AgentRunCommand.class), any(TeamEventSink.class))).thenReturn(teamResult);

        AgentRunResult result = service.run(command(90001L), teamEventSink);

        assertThat(result).isSameAs(teamResult);
        verify(teamRuntimeService).run(command(90001L), teamEventSink);
    }

    @Test
    void runRecordsAgentContextModelSpansAndTokenUsage() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("done"));
        when(traceService.startSpan(any(StartTraceSpanCommand.class)))
                .thenReturn(span(1L, "agent_runtime.run"))
                .thenReturn(span(2L, "context.build"))
                .thenReturn(span(3L, "context.budget.snapshot"))
                .thenReturn(span(4L, "model.invoke"))
                .thenReturn(span(5L, "final.answer.build"))
                .thenReturn(span(6L, "memory.write"));

        service.run(command(90001L));

        ArgumentCaptor<StartTraceSpanCommand> startCaptor = ArgumentCaptor.forClass(StartTraceSpanCommand.class);
        verify(traceService, times(6)).startSpan(startCaptor.capture());
        assertThat(startCaptor.getAllValues()).extracting("spanName")
                .containsExactly("agent_runtime.run", "context.build", "context.budget.snapshot", "model.invoke", "final.answer.build", "memory.write");
        StartTraceSpanCommand snapshotSpan = startCaptor.getAllValues().get(2);
        assertThat(snapshotSpan.parentSpanId()).isEqualTo(2L);
        assertThat(snapshotSpan.attributes().get("contextBudgetSnapshot").get("apiMessagesTokens").asInt()).isEqualTo(20);
        assertThat(snapshotSpan.attributes().get("contextBudgetSnapshot").get("remainingTokens").asInt()).isEqualTo(980);
        assertThat(snapshotSpan.attributes().get("contextBudgetSnapshot").get("truncated").asBoolean()).isFalse();
        StartTraceSpanCommand modelSpan = startCaptor.getAllValues().get(3);
        assertThat(modelSpan.attributes().get("messageCount").asInt()).isEqualTo(2);
        assertThat(modelSpan.attributes().get("toolSpecCount").asInt()).isZero();
        assertThat(modelSpan.attributes().get("stream").asBoolean()).isFalse();

        ArgumentCaptor<FinishTraceSpanCommand> finishCaptor = ArgumentCaptor.forClass(FinishTraceSpanCommand.class);
        verify(traceService, times(6)).finishSpan(finishCaptor.capture());
        assertThat(finishCaptor.getAllValues()).extracting("status")
                .containsExactly("SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS");
        FinishTraceSpanCommand modelFinish = finishCaptor.getAllValues().stream()
                .filter(command -> Long.valueOf(4L).equals(command.spanId()))
                .findFirst()
                .orElseThrow();
        assertThat(modelFinish.attributes().get("providerId").asLong()).isEqualTo(40001L);
        assertThat(modelFinish.attributes().get("providerType").asText()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(modelFinish.attributes().get("modelName").asText()).isEqualTo("mock-chat");
        assertThat(modelFinish.attributes().get("promptTokens").asInt()).isEqualTo(2);
        assertThat(modelFinish.attributes().get("completionTokens").asInt()).isEqualTo(2);
        assertThat(modelFinish.attributes().get("totalTokens").asInt()).isEqualTo(4);
        assertThat(modelFinish.attributes().get("estimatedTokens").asBoolean()).isTrue();

        ArgumentCaptor<RecordTokenUsageCommand> tokenCaptor = ArgumentCaptor.forClass(RecordTokenUsageCommand.class);
        verify(tokenUsageService).record(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().traceId()).isEqualTo("trace-1");
        assertThat(tokenCaptor.getValue().spanId()).isEqualTo(4L);
        assertThat(tokenCaptor.getValue().modelConfigId()).isEqualTo(30001L);
        assertThat(tokenCaptor.getValue().providerId()).isEqualTo(40001L);
        assertThat(tokenCaptor.getValue().providerType()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(tokenCaptor.getValue().totalTokens()).isEqualTo(4);
    }

    @Test
    void runRecordsStep13RuntimeTraceSpans() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(agentTool("calculator", AgentToolSourceType.SKILL)));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@skill:calculator {\"expression\":\"1+2\"}"))
                .thenReturn(modelResult("The result is 3."));
        when(agentToolDispatcher.dispatch(any())).thenReturn(new AgentToolDispatchResult(
                true,
                "calculator",
                AgentToolSourceType.SKILL,
                objectMapper.createObjectNode().put("result", "3"),
                null
        ));
        when(traceService.startSpan(any(StartTraceSpanCommand.class)))
                .thenAnswer(invocation -> {
                    StartTraceSpanCommand command = invocation.getArgument(0);
                    return span((long) (100 + command.spanName().hashCode()), command.spanName());
                });

        service.run(command(90001L));

        ArgumentCaptor<StartTraceSpanCommand> startCaptor = ArgumentCaptor.forClass(StartTraceSpanCommand.class);
        verify(traceService, times(10)).startSpan(startCaptor.capture());
        assertThat(startCaptor.getAllValues()).extracting("spanName")
                .contains(
                        "tool.validate",
                        "tool.plan",
                        "tool.execute",
                        "final.answer.build",
                        "memory.write"
                );

        StartTraceSpanCommand validateSpan = findSpan(startCaptor.getAllValues(), "tool.validate");
        assertThat(validateSpan.attributes().get("batchSize").asInt()).isEqualTo(1);
        assertThat(validateSpan.attributes().get("invalidCount").asInt()).isZero();
        assertThat(validateSpan.attributes().get("toolKeys").get(0).asText()).isEqualTo("skill:calculator");

        StartTraceSpanCommand planSpan = findSpan(startCaptor.getAllValues(), "tool.plan");
        assertThat(planSpan.attributes().get("toolCount").asInt()).isEqualTo(1);
        assertThat(planSpan.attributes().get("groupCount").asInt()).isEqualTo(1);
        assertThat(planSpan.attributes().get("parallelGroupCount").asInt()).isZero();

        StartTraceSpanCommand finalAnswerSpan = findSpan(startCaptor.getAllValues(), "final.answer.build");
        assertThat(finalAnswerSpan.attributes().get("rawChars").asInt()).isEqualTo("The result is 3.".length());
        assertThat(finalAnswerSpan.attributes().get("finalChars").asInt()).isEqualTo("The result is 3.".length());
        assertThat(finalAnswerSpan.attributes().get("changed").asBoolean()).isFalse();
    }

    @Test
    void runReusesExistingConversation() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("done"));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.conversationId()).isEqualTo(90001L);
        verify(conversationRepository, never()).insertConversation(any());
    }

    @Test
    void runRejectsExistingConversationOutsideCurrentScope() {
        ConversationEntity conversation = conversation();
        conversation.setUserId(99999L);
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation);

        assertThatThrownBy(() -> service.run(command(90001L)))
                .isInstanceOf(BizException.class);

        verify(conversationRepository, never()).insertMessage(any());
        verify(modelInvokeService, never()).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void runStoresUserMessageWhenModelFails() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenThrow(new BizException(ErrorCode.MODEL_INVOKE_FAILED, "model down"));
        when(traceService.startSpan(any(StartTraceSpanCommand.class)))
                .thenReturn(span(1L, "agent_runtime.run"))
                .thenReturn(span(2L, "context.build"))
                .thenReturn(span(3L, "context.budget.snapshot"))
                .thenReturn(span(4L, "model.invoke"));

        assertThatThrownBy(() -> service.run(command(90001L)))
                .isInstanceOf(BizException.class);

        verify(conversationRepository).insertMessage(any());
        verify(memoryWriteService, never()).record(any(RecordMemoryCommand.class));
        verify(conversationRepository, never()).insertMessage(argThat(message -> "assistant".equals(message.getRole())));

        ArgumentCaptor<FinishTraceSpanCommand> finishCaptor = ArgumentCaptor.forClass(FinishTraceSpanCommand.class);
        verify(traceService, times(4)).finishSpan(finishCaptor.capture());
        assertThat(finishCaptor.getAllValues()).extracting("status")
                .containsExactly("SUCCESS", "SUCCESS", "FAILED", "FAILED");
    }

    @Test
    void runExecutesSkillToolCallThenAsksModelForFinalAnswer() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(agentTool("calculator", AgentToolSourceType.SKILL)));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@skill:calculator {\"expression\":\"1+2\"}"))
                .thenReturn(modelResult("The result is 3."));
        when(agentToolDispatcher.dispatch(any())).thenReturn(new AgentToolDispatchResult(
                true,
                "calculator",
                AgentToolSourceType.SKILL,
                objectMapper.createObjectNode().put("result", "3"),
                null
        ));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage()).isEqualTo("The result is 3.");
        ArgumentCaptor<AgentToolDispatchCommand> dispatchCaptor = ArgumentCaptor.forClass(AgentToolDispatchCommand.class);
        verify(agentToolDispatcher).dispatch(dispatchCaptor.capture());
        assertThat(dispatchCaptor.getValue().sourceType()).isEqualTo(AgentToolSourceType.SKILL);
        assertThat(dispatchCaptor.getValue().toolName()).isEqualTo("calculator");
        assertThat(dispatchCaptor.getValue().arguments().get("expression").asText()).isEqualTo("1+2");
        verify(skillExecutor, never()).execute(any());
        verify(modelInvokeService, times(2)).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void runCleansInternalToolSyntaxFromFinalAssistantAnswerBeforePersisting() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(agentTool("calculator", AgentToolSourceType.SKILL)));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@skill:calculator {\"expression\":\"1+2\"}"))
                .thenReturn(modelResult("""
                        @skill:calculator {"expression":"1+2"}
                        [tool crash] java.lang.IllegalStateException: stack trace
                        {"result":"3","debug":"internal"}
                        The result is 3.
                        """));
        when(agentToolDispatcher.dispatch(any())).thenReturn(new AgentToolDispatchResult(
                true,
                "calculator",
                AgentToolSourceType.SKILL,
                objectMapper.createObjectNode().put("result", "3"),
                null
        ));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage()).isEqualTo("The result is 3.");
        ArgumentCaptor<com.ls.agent.core.agent.entity.ConversationMessageEntity> messageCaptor =
                ArgumentCaptor.forClass(com.ls.agent.core.agent.entity.ConversationMessageEntity.class);
        verify(conversationRepository, times(2)).insertMessage(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues().get(1).getContent()).isEqualTo("The result is 3.");
        ArgumentCaptor<RecordMemoryCommand> memoryCaptor = ArgumentCaptor.forClass(RecordMemoryCommand.class);
        verify(memoryWriteService, times(2)).record(memoryCaptor.capture());
        RecordMemoryCommand summary = memoryCaptor.getAllValues().get(0);
        assertThat(summary.content()).contains("The result is 3.");
        assertThat(summary.content())
                .doesNotContain("@skill:")
                .doesNotContain("[tool crash]")
                .doesNotContain("\"debug\"");
    }

    @Test
    void runReturnsReadableFallbackWhenFinalAnswerStillLooksLikeToolCallAfterToolFailures() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithoutTools());
        when(agentToolResolver.resolve(any())).thenReturn(List.of());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@skill:calculator {\"expression\":\"1+2\"}"))
                .thenReturn(modelResult("@skill:calculator {\"expression\":\"1+2\"}"))
                .thenReturn(modelResult("@skill:calculator {\"expression\":\"1+2\"}"))
                .thenReturn(modelResult("@skill:calculator {\"expression\":\"1+2\"}"));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage())
                .contains("I could not produce a reliable final answer")
                .doesNotContain("@skill:")
                .doesNotContain("{\"expression\"");
        ArgumentCaptor<com.ls.agent.core.agent.entity.ConversationMessageEntity> messageCaptor =
                ArgumentCaptor.forClass(com.ls.agent.core.agent.entity.ConversationMessageEntity.class);
        verify(conversationRepository, times(2)).insertMessage(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues().get(1).getContent()).isEqualTo(result.assistantMessage());
    }

    @Test
    void runStopsToolLoopAndFallsBackWhenToolIsUnavailable() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithoutTools());
        when(agentToolResolver.resolve(any())).thenReturn(List.of());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@skill:calculator {\"expression\":\"1+2\"}"))
                .thenReturn(modelResult("I cannot use calculator in this context."));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage()).isEqualTo("I cannot use calculator in this context.");
        verify(agentToolDispatcher, never()).dispatch(any());
        verify(skillExecutor, never()).execute(any());
        verify(mcpToolExecutor, never()).execute(any());
        assertThat(result.toolEvents()).isEmpty();
        ArgumentCaptor<ModelInvokeCommand> modelCaptor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService, times(2)).invoke(modelCaptor.capture());
        assertThat(modelCaptor.getAllValues().get(1).messages()).anySatisfy(message -> {
            assertThat(message.role()).isEqualTo("tool");
            assertThat(message.content()).contains("[tool unavailable]", "calculator");
        });
        assertThat(modelCaptor.getAllValues().get(1).messages()).anySatisfy(message -> {
            assertThat(message.role()).isEqualTo("user");
            assertThat(message.content()).contains("Do NOT output tool-call format");
        });
    }

    @Test
    void runCompactsLongToolObservationBeforeNextModelRoundAndRecordsTraceSpan() {
        String longOutput = "x".repeat(1_500);
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(agentTool("calculator", AgentToolSourceType.SKILL)));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@skill:calculator {\"expression\":\"1+2\"}"))
                .thenReturn(modelResult("The result is available."));
        when(agentToolDispatcher.dispatch(any())).thenReturn(new AgentToolDispatchResult(
                true,
                "calculator",
                AgentToolSourceType.SKILL,
                objectMapper.createObjectNode().put("raw", longOutput),
                null
        ));
        when(traceService.startSpan(any(StartTraceSpanCommand.class)))
                .thenReturn(span(1L, "agent_runtime.run"))
                .thenReturn(span(2L, "context.build"))
                .thenReturn(span(3L, "context.budget.snapshot"))
                .thenReturn(span(4L, "model.invoke"))
                .thenReturn(span(5L, "tool.execute"))
                .thenReturn(span(6L, "compact.micro"))
                .thenReturn(span(7L, "model.invoke"));

        service.run(command(90001L));

        ArgumentCaptor<ModelInvokeCommand> modelCaptor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService, times(2)).invoke(modelCaptor.capture());
        ModelMessage compactedToolMessage = modelCaptor.getAllValues().get(1).messages().stream()
                .filter(message -> "tool".equals(message.role()))
                .findFirst()
                .orElseThrow();
        assertThat(compactedToolMessage.content())
                .startsWith("[compact.micro]")
                .contains("originalChars=")
                .hasSizeLessThan(700);

        ArgumentCaptor<StartTraceSpanCommand> startCaptor = ArgumentCaptor.forClass(StartTraceSpanCommand.class);
        verify(traceService, times(11)).startSpan(startCaptor.capture());
        StartTraceSpanCommand compactSpan = findSpan(startCaptor.getAllValues(), "compact.micro");
        assertThat(compactSpan.spanName()).isEqualTo("compact.micro");
        assertThat(compactSpan.attributes().get("role").asText()).isEqualTo("tool");
        assertThat(compactSpan.attributes().get("originalChars").asInt()).isGreaterThan(1_000);
        assertThat(compactSpan.attributes().get("compactedChars").asInt()).isLessThan(700);
    }

    @Test
    void runMicroCompactsLargeApiMessagesBeforeEveryModelInvoke() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithLargeTransientMessages());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("done"));
        when(traceService.startSpan(any(StartTraceSpanCommand.class)))
                .thenReturn(span(1L, "agent_runtime.run"))
                .thenReturn(span(2L, "context.build"))
                .thenReturn(span(3L, "context.budget.snapshot"))
                .thenReturn(span(4L, "compact.micro"))
                .thenReturn(span(5L, "compact.micro"))
                .thenReturn(span(6L, "compact.micro"))
                .thenReturn(span(7L, "model.invoke"));

        service.run(command(90001L));

        ArgumentCaptor<ModelInvokeCommand> modelCaptor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService).invoke(modelCaptor.capture());
        List<ModelMessage> messages = modelCaptor.getValue().messages();
        assertThat(messages).anySatisfy(message -> assertThat(message.content())
                .startsWith("[compact.micro]")
                .contains("originalChars="));
        assertThat(messages).noneSatisfy(message -> assertThat(message.content()).contains("r".repeat(900)));
        assertThat(messages).noneSatisfy(message -> assertThat(message.content()).contains("j".repeat(900)));
        assertThat(messages).noneSatisfy(message -> assertThat(message.content()).contains("e".repeat(900)));

        ArgumentCaptor<StartTraceSpanCommand> startCaptor = ArgumentCaptor.forClass(StartTraceSpanCommand.class);
        verify(traceService, times(9)).startSpan(startCaptor.capture());
        assertThat(startCaptor.getAllValues()).extracting("spanName")
                .containsExactly(
                        "agent_runtime.run",
                        "context.build",
                        "context.budget.snapshot",
                        "compact.micro",
                        "compact.micro",
                        "compact.micro",
                        "model.invoke",
                        "final.answer.build",
                        "memory.write"
                );
    }

    @Test
    void runMicroCompactsInitialContextOnlyOnceBeforeFirstModelInvoke() {
        MicroCompactService compactService = mock(MicroCompactService.class);
        when(compactService.compact(any(), any())).thenAnswer(invocation -> {
            String content = invocation.getArgument(1);
            return new MicroCompactResult(content, false, content.length(), content.length());
        });
        DefaultAgentRuntimeService runtimeService = runtimeService(compactService);
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("done"));

        runtimeService.run(command(90001L));

        verify(compactService, times(2)).compact(any(), any());
    }

    @Test
    void runDoesNotPreselectCalculatorWhenUserAsksArithmetic() {
        AgentRunCommand command = new AgentRunCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "计算 1+2 等于多少",
                "trace-1",
                null,
                null,
                1000
        );
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("1+2 等于 3。"));

        AgentRunResult result = service.run(command);

        assertThat(result.assistantMessage()).isEqualTo("1+2 等于 3。");
        assertThat(result.toolEvents()).isEmpty();
        verify(agentToolDispatcher, never()).dispatch(any());
        verify(skillExecutor, never()).execute(any());

        ArgumentCaptor<ModelInvokeCommand> modelCaptor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService).invoke(modelCaptor.capture());
        assertThat(modelCaptor.getValue().messages()).noneSatisfy(message -> assertThat(message.role()).isEqualTo("tool"));
    }

    @Test
    void runDoesNotPreselectCalculatorForCompactChineseArithmeticQuestion() {
        AgentRunCommand command = new AgentRunCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "1+1等于几",
                "trace-1",
                null,
                null,
                1000
        );
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("1+1 等于 2。"));

        AgentRunResult result = service.run(command);

        assertThat(result.assistantMessage()).isEqualTo("1+1 等于 2。");
        verify(agentToolDispatcher, never()).dispatch(any());
        verify(skillExecutor, never()).execute(any());
    }

    @Test
    void runExecutesMcpToolCallThenAsksModelForFinalAnswer() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(agentTool("read_file", AgentToolSourceType.MCP)));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@mcp:read_file {\"path\":\"/tmp/demo.txt\"}"))
                .thenReturn(modelResult("The file says demo."));
        when(agentToolDispatcher.dispatch(any())).thenReturn(new AgentToolDispatchResult(
                true,
                "read_file",
                AgentToolSourceType.MCP,
                objectMapper.createObjectNode().put("content", "demo"),
                null
        ));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage()).isEqualTo("The file says demo.");
        verify(agentToolDispatcher).dispatch(any());
        verify(mcpToolExecutor, never()).execute(any());
        verify(modelInvokeService, times(2)).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void runExecutesMultipleToolCallsFromOneModelTurnAsOrderedBatch() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(
                agentTool("calculator", AgentToolSourceType.SKILL),
                agentTool("read_file", AgentToolSourceType.MCP)
        ));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("""
                        @skill:calculator {"expression":"1+2"}
                        @mcp:read_file {"path":"/tmp/demo.txt"}
                        """))
                .thenReturn(modelResult("The calculation is 3 and the file says demo."));
        when(agentToolDispatcher.dispatch(any())).thenAnswer(invocation -> {
            AgentToolDispatchCommand dispatchCommand = invocation.getArgument(0);
            if ("calculator".equals(dispatchCommand.toolName())) {
                return new AgentToolDispatchResult(
                        true,
                        "calculator",
                        AgentToolSourceType.SKILL,
                        objectMapper.createObjectNode().put("result", "3"),
                        null
                );
            }
            return new AgentToolDispatchResult(
                    true,
                    "read_file",
                    AgentToolSourceType.MCP,
                    objectMapper.createObjectNode().put("content", "demo"),
                    null
            );
        });

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage()).isEqualTo("The calculation is 3 and the file says demo.");
        assertThat(result.toolEvents()).extracting("type")
                .containsExactly("action", "observation", "action", "observation");
        assertThat(result.toolEvents()).extracting("toolName")
                .containsExactly("calculator", "calculator", "read_file", "read_file");

        ArgumentCaptor<AgentToolDispatchCommand> dispatchCaptor = ArgumentCaptor.forClass(AgentToolDispatchCommand.class);
        verify(agentToolDispatcher, times(2)).dispatch(dispatchCaptor.capture());
        assertThat(dispatchCaptor.getAllValues()).extracting("toolName")
                .containsExactly("calculator", "read_file");

        ArgumentCaptor<ModelInvokeCommand> modelCaptor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService, times(2)).invoke(modelCaptor.capture());
        List<ModelMessage> secondRoundMessages = modelCaptor.getAllValues().get(1).messages();
        assertThat(secondRoundMessages.stream()
                .filter(message -> "tool".equals(message.role()))
                .map(ModelMessage::content)
                .toList())
                .containsExactly("{\"result\":\"3\"}", "{\"content\":\"demo\"}");
    }

    @Test
    void runExecutesParallelSafeToolBatchAndJoinsObservationsInOriginalOrder() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicBoolean calculatorWaitedForReadFile = new AtomicBoolean(false);
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(
                agentTool("calculator", AgentToolSourceType.SKILL, true, List.of("calc")),
                agentTool("read_file", AgentToolSourceType.MCP, true, List.of("file:/tmp/demo.txt"))
        ));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("""
                        @skill:calculator {"expression":"1+2"}
                        @mcp:read_file {"path":"/tmp/demo.txt"}
                        """))
                .thenReturn(modelResult("The calculation is 3 and the file says demo."));
        when(agentToolDispatcher.dispatch(any())).thenAnswer(invocation -> {
            AgentToolDispatchCommand dispatchCommand = invocation.getArgument(0);
            bothStarted.countDown();
            if ("calculator".equals(dispatchCommand.toolName())) {
                calculatorWaitedForReadFile.set(bothStarted.await(1, TimeUnit.SECONDS));
                return new AgentToolDispatchResult(
                        true,
                        "calculator",
                        AgentToolSourceType.SKILL,
                        objectMapper.createObjectNode().put("result", "3"),
                        null
                );
            }
            return new AgentToolDispatchResult(
                    true,
                    "read_file",
                    AgentToolSourceType.MCP,
                    objectMapper.createObjectNode().put("content", "demo"),
                    null
            );
        });

        AgentRunResult result = service.run(command(90001L));

        assertThat(calculatorWaitedForReadFile).isTrue();
        assertThat(result.toolEvents()).extracting("toolName")
                .containsExactly("calculator", "calculator", "read_file", "read_file");

        ArgumentCaptor<ModelInvokeCommand> modelCaptor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService, times(2)).invoke(modelCaptor.capture());
        assertThat(modelCaptor.getAllValues().get(1).messages().stream()
                .filter(message -> "tool".equals(message.role()))
                .map(ModelMessage::content)
                .toList())
                .containsExactly("{\"result\":\"3\"}", "{\"content\":\"demo\"}");
        assertThat(result.assistantMessage()).isEqualTo("The calculation is 3 and the file says demo.");
    }

    @Test
    void runBlocksHighRiskToolAndReturnsConfirmationRequiredEvent() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(
                agentTool("deploy", AgentToolSourceType.SKILL, AgentToolRiskLevel.HIGH, false, List.of("prod"))
        ));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@skill:deploy {\"target\":\"prod\"}"))
                .thenReturn(modelResult("Deployment requires confirmation before it can continue."));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage()).isEqualTo("Deployment requires confirmation before it can continue.");
        verify(agentToolDispatcher, never()).dispatch(any());
        assertThat(result.toolEvents()).extracting("type", "toolName")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("action", "deploy"),
                        org.assertj.core.groups.Tuple.tuple("tool_confirm_required", "deploy")
                );
        assertThat(result.toolEvents().get(1).content()).contains("[tool confirm required]", "skill:deploy", "risk=HIGH");
        assertThat(result.toolEvents().get(1).metadata())
                .containsEntry("toolKey", "skill:deploy")
                .containsKey("pendingToolCall");

        ArgumentCaptor<ModelInvokeCommand> modelCaptor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService, times(2)).invoke(modelCaptor.capture());
        assertThat(modelCaptor.getAllValues().get(1).messages().stream()
                .filter(message -> "tool".equals(message.role()))
                .map(ModelMessage::content)
                .toList())
                .containsExactly("[tool confirm required] skill:deploy risk=HIGH");
    }

    @Test
    void runExecutesHighRiskToolWhenItIsExplicitlyConfirmed() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(
                agentTool("deploy", AgentToolSourceType.SKILL, AgentToolRiskLevel.HIGH, false, List.of("prod"))
        ));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@skill:deploy {\"target\":\"prod\"}"))
                .thenReturn(modelResult("Deployment completed."));
        when(agentToolDispatcher.dispatch(any())).thenReturn(new AgentToolDispatchResult(
                true,
                "deploy",
                AgentToolSourceType.SKILL,
                objectMapper.createObjectNode().put("status", "deployed"),
                null
        ));

        AgentRunResult result = service.run(commandWithConfirmedTools(90001L, List.of("skill:deploy")));

        assertThat(result.assistantMessage()).isEqualTo("Deployment completed.");
        assertThat(result.toolEvents()).extracting("type", "toolName")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("action", "deploy"),
                        org.assertj.core.groups.Tuple.tuple("observation", "deploy")
        );
        verify(agentToolDispatcher).dispatch(any());
    }

    @Test
    void runResumesConfirmedPendingToolCallWithoutAskingModelToRegenerateToolCall() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(
                agentTool("deploy", AgentToolSourceType.SKILL, AgentToolRiskLevel.HIGH, false, List.of("prod"))
        ));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("Deployment completed."));

        AgentRunResult result = service.run(commandWithPendingToolCall(90001L));

        assertThat(result.assistantMessage()).isEqualTo("Deployment completed.");
        verify(agentToolDispatcher, never()).dispatch(any());
        verify(modelInvokeService).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void runExecutesMediumRiskToolAndRecordsRiskInToolSpan() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(agentToolResolver.resolve(any())).thenReturn(List.of(
                agentTool("web_search", AgentToolSourceType.SKILL, AgentToolRiskLevel.MEDIUM, true, List.of("web"))
        ));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@skill:web_search {\"query\":\"agent\"}"))
                .thenReturn(modelResult("Search completed."));
        when(agentToolDispatcher.dispatch(any())).thenReturn(new AgentToolDispatchResult(
                true,
                "web_search",
                AgentToolSourceType.SKILL,
                objectMapper.createObjectNode().put("title", "Agent Platform"),
                null
        ));
        when(traceService.startSpan(any(StartTraceSpanCommand.class)))
                .thenAnswer(invocation -> {
                    StartTraceSpanCommand command = invocation.getArgument(0);
                    return span((long) (100 + command.spanName().hashCode()), command.spanName());
                });

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage()).isEqualTo("Search completed.");
        verify(agentToolDispatcher).dispatch(any());

        ArgumentCaptor<StartTraceSpanCommand> startCaptor = ArgumentCaptor.forClass(StartTraceSpanCommand.class);
        verify(traceService, times(10)).startSpan(startCaptor.capture());
        StartTraceSpanCommand toolSpan = findSpan(startCaptor.getAllValues(), "tool.execute");
        assertThat(toolSpan.spanName()).isEqualTo("tool.execute");
        assertThat(toolSpan.attributes().get("riskLevel").asText()).isEqualTo("MEDIUM");
        assertThat(toolSpan.attributes().get("readOnly").asBoolean()).isTrue();
        assertThat(toolSpan.attributes().get("resourceKeys").get(0).asText()).isEqualTo("web");
        assertThat(toolSpan.attributes().get("argumentsSummary").asText()).contains("query");

        ArgumentCaptor<FinishTraceSpanCommand> finishCaptor = ArgumentCaptor.forClass(FinishTraceSpanCommand.class);
        verify(traceService, times(10)).finishSpan(finishCaptor.capture());
        FinishTraceSpanCommand toolFinish = finishCaptor.getAllValues().stream()
                .filter(command -> Long.valueOf(100L + "tool.execute".hashCode()).equals(command.spanId()))
                .findFirst()
                .orElseThrow();
        assertThat(toolFinish.attributes().get("resultSummary").asText()).contains("Agent Platform");
        assertThat(toolFinish.attributes().get("success").asBoolean()).isTrue();
    }

    @Test
    void runDoesNotDispatchSkillToolCallOutsideContextTools() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithoutTools());
        when(agentToolResolver.resolve(any())).thenReturn(List.of());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@skill:calculator {\"expression\":\"1+2\"}"))
                .thenReturn(modelResult("Calculator is unavailable."));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage()).isEqualTo("Calculator is unavailable.");
        verify(agentToolDispatcher, never()).dispatch(any());
        verify(skillExecutor, never()).execute(any());
        assertThat(result.toolEvents()).isEmpty();
    }

    @Test
    void runDoesNotDispatchMcpToolCallOutsideContextTools() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithoutTools());
        when(agentToolResolver.resolve(any())).thenReturn(List.of());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@mcp:read_file {\"path\":\"/tmp/demo.txt\"}"))
                .thenReturn(modelResult("File tool is unavailable."));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage()).isEqualTo("File tool is unavailable.");
        verify(agentToolDispatcher, never()).dispatch(any());
        verify(mcpToolExecutor, never()).execute(any());
        assertThat(result.toolEvents()).isEmpty();
    }

    @Test
    void traceServiceFailureDoesNotBreakSuccessfulRun() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("done"));
        doThrow(new IllegalStateException("trace down"))
                .when(traceService).startSpan(any(StartTraceSpanCommand.class));
        doThrow(new IllegalStateException("trace down"))
                .when(tokenUsageService).record(any(RecordTokenUsageCommand.class));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage()).isEqualTo("done");
    }

    private AgentRunCommand command(Long conversationId) {
        return command(conversationId, null);
    }

    private AgentRunCommand command(Long conversationId, String agentMode) {
        return commandWithInput(conversationId, "hello", agentMode);
    }

    private AgentRunCommand commandWithInput(Long conversationId, String userInput) {
        return commandWithInput(conversationId, userInput, null);
    }

    private AgentRunCommand commandWithInput(Long conversationId, String userInput, String agentMode) {
        return new AgentRunCommand(
                1L,
                10001L,
                20001L,
                50001L,
                conversationId,
                userInput,
                "trace-1",
                null,
                null,
                1000,
                agentMode
        );
    }

    private AgentRunCommand commandWithConfirmedTools(Long conversationId, List<String> confirmedToolKeys) {
        return new AgentRunCommand(
                1L,
                10001L,
                20001L,
                50001L,
                conversationId,
                "hello",
                "trace-1",
                null,
                null,
                1000,
                null,
                confirmedToolKeys
        );
    }

    private AgentRunCommand commandWithPendingToolCall(Long conversationId) {
        return new AgentRunCommand(
                1L,
                10001L,
                20001L,
                50001L,
                conversationId,
                "hello",
                "trace-1",
                null,
                null,
                1000,
                null,
                List.of("skill:deploy"),
                new PendingToolCallCommand(
                        AgentToolSourceType.SKILL,
                        "deploy",
                        objectMapper.createObjectNode().put("target", "prod")
                ),
                null
        );
    }

    private DefaultAgentRuntimeService runtimeService(MicroCompactService compactService) {
        return runtimeService(compactService, List.of());
    }

    private DefaultAgentRuntimeService runtimeService(MicroCompactService compactService, List<AgentRuntimeHook> runtimeHooks) {
        return new DefaultAgentRuntimeService(
                contextBuilder,
                compactService,
                modelInvokeService,
                agentToolResolver,
                agentToolDispatcher,
                agentToolCallValidator,
                toolExecutionPlanner,
                teamRuntimeService,
                finalResponseSynthesizer,
                conversationRepository,
                memoryWriteService,
                preferenceExtractor,
                traceService,
                tokenUsageService,
                objectMapper,
                runtimeHooks
        );
    }

    private AgentContextDTO context() {
        return new AgentContextDTO(
                30001L,
                profile(),
                List.of(new ModelMessage("system", "You are Nexus"), new ModelMessage("user", "hello")),
                List.of(skill()),
                List.of(mcpTool()),
                20,
                false
        );
    }

    private AgentContextDTO contextWithMemoryStrategy(String mode) {
        return new AgentContextDTO(
                30001L,
                profileWithMemoryStrategy(mode),
                List.of(new ModelMessage("system", "You are Nexus"), new ModelMessage("user", "hello")),
                List.of(skill()),
                List.of(mcpTool()),
                20,
                false
        );
    }

    private AgentContextDTO contextWithSeparatedMessages() {
        return new AgentContextDTO(
                30001L,
                profile(),
                List.of(new ModelMessage("user", "hello")),
                List.of(
                        new ModelMessage("system", "You are Nexus\n[memory] Ada likes concise answers.\nTool specs\n[rag] private reference"),
                        new ModelMessage("user", "hello")
                ),
                List.of(skill()),
                List.of(mcpTool()),
                40,
                false
        );
    }

    private AgentContextDTO contextWithoutTools() {
        return new AgentContextDTO(
                30001L,
                profile(),
                List.of(new ModelMessage("system", "You are Nexus"), new ModelMessage("user", "hello")),
                List.of(),
                List.of(),
                20,
                false
        );
    }

    private AgentContextDTO contextWithWeatherSkill() {
        return new AgentContextDTO(
                30001L,
                profile(),
                List.of(new ModelMessage("system", "You are Nexus"), new ModelMessage("user", "重庆")),
                List.of(new ModelMessage("system", "You are Nexus"), new ModelMessage("user", "重庆")),
                List.of(weatherSkill()),
                List.of(),
                20,
                false
        );
    }

    private AgentContextDTO contextWithWeatherMcpTool() {
        return new AgentContextDTO(
                30001L,
                profile(),
                List.of(new ModelMessage("system", "You are Nexus"), new ModelMessage("user", "重庆")),
                List.of(new ModelMessage("system", "You are Nexus"), new ModelMessage("user", "重庆")),
                List.of(),
                List.of(weatherMcpTool()),
                20,
                false
        );
    }

    private AgentContextDTO contextWithExecutionMode(String executionMode) {
        return new AgentContextDTO(
                30001L,
                profile(executionMode),
                List.of(new ModelMessage("system", "You are Nexus"), new ModelMessage("user", "hello")),
                List.of(skill()),
                List.of(mcpTool()),
                20,
                false
        );
    }

    private AgentContextDTO contextWithLargeTransientMessages() {
        return new AgentContextDTO(
                30001L,
                profile(),
                List.of(new ModelMessage("user", "hello")),
                List.of(
                        new ModelMessage("system", "You are Nexus"),
                        new ModelMessage("assistant", "[rag] " + "r".repeat(1_200)),
                        new ModelMessage("assistant", "{\"items\":\"" + "j".repeat(1_200) + "\"}"),
                        new ModelMessage("tool", "[tool crash] " + "e".repeat(1_200)),
                        new ModelMessage("user", "hello")
                ),
                List.of(skill()),
                List.of(mcpTool()),
                1_000,
                false
        );
    }

    private ProfileDTO profile() {
        return profile("BASIC");
    }

    private ProfileDTO profile(String executionMode) {
        return new ProfileDTO(
                50001L,
                20001L,
                "General Assistant",
                "GENERAL",
                "Stage 1 profile",
                30001L,
                "Be concise.",
                null,
                6,
                executionMode,
                "PRIVATE",
                "DRAFT",
                List.of(),
                List.of()
        );
    }

    private ProfileDTO profileWithMemoryStrategy(String mode) {
        return new ProfileDTO(
                50001L,
                20001L,
                "General Assistant",
                "GENERAL",
                "Stage 1 profile",
                30001L,
                "Be concise.",
                objectMapper.createObjectNode().put("mode", mode),
                6,
                "BASIC",
                "PRIVATE",
                "DRAFT",
                List.of(),
                List.of()
        );
    }

    private ModelInvokeResult modelResult(String assistantMessage) {
        return new ModelInvokeResult(
                30001L,
                40001L,
                "OPENAI_COMPATIBLE",
                "mock-chat",
                assistantMessage,
                new ModelUsageDTO(2, 2, 4, true)
        );
    }

    private ModelInvokeResult modelResultWithToolCall(String sourceType, String toolName, JsonNode arguments) {
        return new ModelInvokeResult(
                30001L,
                40001L,
                "OPENAI_COMPATIBLE",
                "mock-chat",
                "",
                new ModelUsageDTO(2, 2, 4, true),
                List.of(new ModelToolCallDTO(sourceType, toolName, arguments))
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

    private StartTraceSpanCommand findSpan(List<StartTraceSpanCommand> spans, String spanName) {
        return spans.stream()
                .filter(span -> spanName.equals(span.spanName()))
                .findFirst()
                .orElseThrow();
    }

    private ConversationEntity conversation() {
        ConversationEntity entity = new ConversationEntity();
        entity.setId(90001L);
        entity.setTenantId(1L);
        entity.setApplicationId(20001L);
        entity.setUserId(10001L);
        entity.setProfileId(50001L);
        entity.setStatus("ACTIVE");
        return entity;
    }

    private SkillDTO skill() {
        return new SkillDTO(
                1L,
                "calculator",
                "Calculator",
                "Evaluate arithmetic expressions.",
                "BUILTIN",
                "GLOBAL",
                "ENABLED",
                objectMapper.createObjectNode()
        );
    }

    private SkillDTO weatherSkill() {
        return new SkillDTO(
                2L,
                "weather",
                "Weather",
                "Return current weather.",
                "BUILTIN",
                "GLOBAL",
                "ENABLED",
                objectMapper.createObjectNode()
        );
    }

    private McpToolDTO mcpTool() {
        return new McpToolDTO(
                1L,
                10L,
                "read_file",
                "Read readonly file.",
                "AVAILABLE",
                objectMapper.createObjectNode()
        );
    }

    private McpToolDTO weatherMcpTool() {
        return new McpToolDTO(
                2L,
                11L,
                "weather.current",
                "Get current demo weather by city.",
                "AVAILABLE",
                objectMapper.createObjectNode()
                        .put("type", "object")
                        .set("properties", objectMapper.createObjectNode()
                                .set("city", objectMapper.createObjectNode()
                                        .put("type", "string")))
        );
    }

    private AgentToolDTO agentTool(String name, AgentToolSourceType sourceType) {
        return agentTool(name, sourceType, AgentToolRiskLevel.LOW, false, List.of());
    }

    private AgentToolDTO agentTool(String name, AgentToolSourceType sourceType, boolean readOnly, List<String> resourceKeys) {
        return agentTool(name, sourceType, AgentToolRiskLevel.LOW, readOnly, resourceKeys);
    }

    private AgentToolDTO agentTool(
            String name,
            AgentToolSourceType sourceType,
            AgentToolRiskLevel riskLevel,
            boolean readOnly,
            List<String> resourceKeys
    ) {
        return new AgentToolDTO(
                name,
                name,
                name + " tool",
                sourceType,
                objectMapper.createObjectNode(),
                riskLevel,
                readOnly,
                resourceKeys
        );
    }
}
