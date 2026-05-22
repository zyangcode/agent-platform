package com.ls.agent.core.agent;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.application.DefaultAgentRuntimeService;
import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.dto.AgentRunResult;
import com.ls.agent.core.agent.entity.ConversationEntity;
import com.ls.agent.core.agent.entity.ConversationMessageEntity;
import com.ls.agent.core.agent.mapper.ConversationMapper;
import com.ls.agent.core.agent.mapper.ConversationMessageMapper;
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
import com.ls.agent.core.skill.dto.SkillDTO;
import com.ls.agent.core.skill.dto.SkillExecuteResult;
import com.ls.agent.core.mcp.dto.McpToolDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private final ConversationMapper conversationMapper = mock(ConversationMapper.class);
    private final ConversationMessageMapper messageMapper = mock(ConversationMessageMapper.class);
    private final MemoryWriteService memoryWriteService = mock(MemoryWriteService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultAgentRuntimeService service = new DefaultAgentRuntimeService(
            contextBuilder,
            modelInvokeService,
            skillExecutor,
            mcpToolExecutor,
            conversationMapper,
            messageMapper,
            memoryWriteService,
            objectMapper
    );

    @Test
    void runCreatesConversationInvokesModelAndStoresMessages() {
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("done"));
        when(conversationMapper.insert(any(ConversationEntity.class))).thenAnswer(invocation -> {
            ConversationEntity entity = invocation.getArgument(0);
            entity.setId(90001L);
            return 1;
        });

        AgentRunResult result = service.run(command(null));

        assertThat(result.conversationId()).isEqualTo(90001L);
        assertThat(result.assistantMessage()).isEqualTo("done");
        assertThat(result.usage().totalTokens()).isEqualTo(4);

        ArgumentCaptor<ConversationMessageEntity> messageCaptor =
                ArgumentCaptor.forClass(ConversationMessageEntity.class);
        verify(messageMapper, times(2)).insert(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues()).extracting("role")
                .containsExactly("user", "assistant");
        assertThat(messageCaptor.getAllValues()).extracting("content")
                .containsExactly("hello", "done");

        ArgumentCaptor<RecordMemoryCommand> memoryCaptor = ArgumentCaptor.forClass(RecordMemoryCommand.class);
        verify(memoryWriteService).record(memoryCaptor.capture());
        assertThat(memoryCaptor.getValue().content()).contains("hello", "done");
    }

    @Test
    void runReusesExistingConversation() {
        when(conversationMapper.selectById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("done"));

        AgentRunResult result = service.run(command(90001L));

        assertThat(result.conversationId()).isEqualTo(90001L);
        verify(conversationMapper, never()).insert(any(ConversationEntity.class));
    }

    @Test
    void runRejectsExistingConversationOutsideCurrentScope() {
        ConversationEntity conversation = conversation();
        conversation.setUserId(99999L);
        when(conversationMapper.selectById(90001L)).thenReturn(conversation);

        assertThatThrownBy(() -> service.run(command(90001L)))
                .isInstanceOf(BizException.class);

        verify(messageMapper, never()).insert(any(ConversationMessageEntity.class));
        verify(modelInvokeService, never()).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void runStoresUserMessageWhenModelFails() {
        when(conversationMapper.selectById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(context());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenThrow(new BizException(ErrorCode.MODEL_INVOKE_FAILED, "model down"));

        assertThatThrownBy(() -> service.run(command(90001L)))
                .isInstanceOf(BizException.class);

        verify(messageMapper).insert(any(ConversationMessageEntity.class));
        verify(memoryWriteService, never()).record(any(RecordMemoryCommand.class));
        verify(messageMapper, never()).insert(argThatMessageRole("assistant"));
    }

    @Test
    void runExecutesSkillToolCallThenAsksModelForFinalAnswer() {
        when(conversationMapper.selectById(90001L)).thenReturn(conversation());
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
    void runExecutesMcpToolCallThenAsksModelForFinalAnswer() {
        when(conversationMapper.selectById(90001L)).thenReturn(conversation());
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
        when(conversationMapper.selectById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithoutTools());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@skill:calculator {\"expression\":\"1+2\"}"));

        assertThatThrownBy(() -> service.run(command(90001L)))
                .isInstanceOf(BizException.class);

        verify(skillExecutor, never()).execute(any());
    }

    @Test
    void runRejectsMcpToolCallOutsideContextTools() {
        when(conversationMapper.selectById(90001L)).thenReturn(conversation());
        when(contextBuilder.build(any(BuildAgentContextCommand.class))).thenReturn(contextWithoutTools());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("@mcp:read_file {\"path\":\"/tmp/demo.txt\"}"));

        assertThatThrownBy(() -> service.run(command(90001L)))
                .isInstanceOf(BizException.class);

        verify(mcpToolExecutor, never()).execute(any());
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
                "mock-chat",
                assistantMessage,
                new ModelUsageDTO(2, 2, 4, true)
        );
    }

    private ConversationMessageEntity argThatMessageRole(String role) {
        return org.mockito.ArgumentMatchers.argThat(message -> role.equals(message.getRole()));
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
