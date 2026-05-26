package com.ls.agent.core.agent;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.api.ConversationRepository;
import com.ls.agent.core.agent.application.DefaultAgentRuntimeService;
import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.dto.AgentRunResult;
import com.ls.agent.core.agent.entity.ConversationEntity;
import com.ls.agent.core.context.api.AgentContextBuilder;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.mcp.api.McpToolExecutor;
import com.ls.agent.core.mcp.dto.McpToolExecuteResult;
import com.ls.agent.core.memory.api.MemoryWriteService;
import com.ls.agent.core.memory.command.RecordMemoryCommand;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.skill.api.SkillExecutor;
import com.ls.agent.core.skill.command.SkillExecuteCommand;
import com.ls.agent.core.skill.dto.SkillDTO;
import com.ls.agent.core.skill.dto.SkillExecuteResult;
import com.ls.agent.core.mcp.dto.McpToolDTO;
import com.ls.agent.core.quota.api.TokenUsageService;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.FinishTraceSpanCommand;
import com.ls.agent.core.quota.command.RecordTokenUsageCommand;
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceSpanDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final MemoryWriteService memoryWriteService = mock(MemoryWriteService.class);
    private final TraceService traceService = mock(TraceService.class);
    private final TokenUsageService tokenUsageService = mock(TokenUsageService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultAgentRuntimeService service = new DefaultAgentRuntimeService(
            contextBuilder,
            modelInvokeService,
            skillExecutor,
            mcpToolExecutor,
            conversationRepository,
            memoryWriteService,
            traceService,
            tokenUsageService,
            objectMapper
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
    }

    @Test
    void runRecordsAgentContextModelSpansAndTokenUsage() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("done"));
        when(traceService.startSpan(any(StartTraceSpanCommand.class)))
                .thenReturn(span(1L, "agent_runtime.run"))
                .thenReturn(span(2L, "context.build"))
                .thenReturn(span(3L, "model.invoke"));

        service.run(command(90001L));

        ArgumentCaptor<StartTraceSpanCommand> startCaptor = ArgumentCaptor.forClass(StartTraceSpanCommand.class);
        verify(traceService, times(3)).startSpan(startCaptor.capture());
        assertThat(startCaptor.getAllValues()).extracting("spanName")
                .containsExactly("agent_runtime.run", "context.build", "model.invoke");

        ArgumentCaptor<FinishTraceSpanCommand> finishCaptor = ArgumentCaptor.forClass(FinishTraceSpanCommand.class);
        verify(traceService, times(3)).finishSpan(finishCaptor.capture());
        assertThat(finishCaptor.getAllValues()).extracting("status")
                .containsExactly("SUCCESS", "SUCCESS", "SUCCESS");

        ArgumentCaptor<RecordTokenUsageCommand> tokenCaptor = ArgumentCaptor.forClass(RecordTokenUsageCommand.class);
        verify(tokenUsageService).record(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().traceId()).isEqualTo("trace-1");
        assertThat(tokenCaptor.getValue().spanId()).isEqualTo(3L);
        assertThat(tokenCaptor.getValue().modelConfigId()).isEqualTo(30001L);
        assertThat(tokenCaptor.getValue().providerId()).isEqualTo(40001L);
        assertThat(tokenCaptor.getValue().providerType()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(tokenCaptor.getValue().totalTokens()).isEqualTo(4);
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
                .thenReturn(span(3L, "model.invoke"));

        assertThatThrownBy(() -> service.run(command(90001L)))
                .isInstanceOf(BizException.class);

        verify(conversationRepository).insertMessage(any());
        verify(memoryWriteService, never()).record(any(RecordMemoryCommand.class));
        verify(conversationRepository, never()).insertMessage(argThat(message -> "assistant".equals(message.getRole())));

        ArgumentCaptor<FinishTraceSpanCommand> finishCaptor = ArgumentCaptor.forClass(FinishTraceSpanCommand.class);
        verify(traceService, times(3)).finishSpan(finishCaptor.capture());
        assertThat(finishCaptor.getAllValues()).extracting("status")
                .containsExactly("SUCCESS", "FAILED", "FAILED");
    }

    @Test
    void runExecutesSkillToolCallThenAsksModelForFinalAnswer() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@skill:calculator {\"expression\":\"1+2\"}"))
                .thenReturn(modelResult("The result is 3."));
        when(skillExecutor.execute(any())).thenReturn(new SkillExecuteResult(
                true,
                "calculator",
                objectMapper.createObjectNode().put("result", "3"),
                null
        ));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage()).isEqualTo("The result is 3.");
        verify(skillExecutor).execute(any());
        verify(modelInvokeService, times(2)).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void runAutoExecutesCalculatorWhenUserAsksArithmeticAndSkillIsAvailable() {
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
        when(skillExecutor.execute(any())).thenReturn(new SkillExecuteResult(
                true,
                "calculator",
                objectMapper.createObjectNode().put("result", "3"),
                null
        ));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("1+2 等于 3。"));

        AgentRunResult result = service.run(command);

        assertThat(result.assistantMessage()).isEqualTo("1+2 等于 3。");
        assertThat(result.toolEvents()).extracting("type").containsExactly("action", "observation");
        assertThat(result.toolEvents()).extracting("toolName").containsExactly("calculator", "calculator");
        ArgumentCaptor<SkillExecuteCommand> skillCaptor = ArgumentCaptor.forClass(SkillExecuteCommand.class);
        verify(skillExecutor).execute(skillCaptor.capture());
        assertThat(skillCaptor.getValue().skillCode()).isEqualTo("calculator");
        assertThat(skillCaptor.getValue().arguments().get("expression").asText()).isEqualTo("1+2");

        ArgumentCaptor<ModelInvokeCommand> modelCaptor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService).invoke(modelCaptor.capture());
        assertThat(modelCaptor.getValue().messages()).anySatisfy(message -> {
            assertThat(message.role()).isEqualTo("tool");
            assertThat(message.content()).contains("\"result\":\"3\"");
        });
    }

    @Test
    void runAutoExecutesCalculatorForCompactChineseArithmeticQuestion() {
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
        when(skillExecutor.execute(any())).thenReturn(new SkillExecuteResult(
                true,
                "calculator",
                objectMapper.createObjectNode().put("result", "2"),
                null
        ));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("1+1 等于 2。"));

        AgentRunResult result = service.run(command);

        assertThat(result.assistantMessage()).isEqualTo("1+1 等于 2。");
        ArgumentCaptor<SkillExecuteCommand> skillCaptor = ArgumentCaptor.forClass(SkillExecuteCommand.class);
        verify(skillExecutor).execute(skillCaptor.capture());
        assertThat(skillCaptor.getValue().arguments().get("expression").asText()).isEqualTo("1+1");
    }

    @Test
    void runExecutesMcpToolCallThenAsksModelForFinalAnswer() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@mcp:read_file {\"path\":\"/tmp/demo.txt\"}"))
                .thenReturn(modelResult("The file says demo."));
        when(mcpToolExecutor.execute(any())).thenReturn(new McpToolExecuteResult(
                true,
                "read_file",
                objectMapper.createObjectNode().put("content", "demo"),
                null
        ));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.assistantMessage()).isEqualTo("The file says demo.");
        verify(mcpToolExecutor).execute(any());
        verify(modelInvokeService, times(2)).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void runRejectsSkillToolCallOutsideContextTools() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithoutTools());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@skill:calculator {\"expression\":\"1+2\"}"));

        assertThatThrownBy(() -> service.run(command(90001L)))
                .isInstanceOf(BizException.class);

        verify(skillExecutor, never()).execute(any());
    }

    @Test
    void runRejectsMcpToolCallOutsideContextTools() {
        when(conversationRepository.findConversationById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithoutTools());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@mcp:read_file {\"path\":\"/tmp/demo.txt\"}"));

        assertThatThrownBy(() -> service.run(command(90001L)))
                .isInstanceOf(BizException.class);

        verify(mcpToolExecutor, never()).execute(any());
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
                1000
        );
    }

    private AgentContextDTO context() {
        return new AgentContextDTO(
                30001L,
                profile(),
                List.of(new ModelMessage("system", "You are AgentX"), new ModelMessage("user", "hello")),
                List.of(skill()),
                List.of(mcpTool()),
                20,
                false
        );
    }

    private AgentContextDTO contextWithoutTools() {
        return new AgentContextDTO(
                30001L,
                profile(),
                List.of(new ModelMessage("system", "You are AgentX"), new ModelMessage("user", "hello")),
                List.of(),
                List.of(),
                20,
                false
        );
    }

    private ProfileDTO profile() {
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
}
