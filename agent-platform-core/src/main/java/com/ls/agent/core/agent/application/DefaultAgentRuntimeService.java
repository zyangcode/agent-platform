package com.ls.agent.core.agent.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.api.AgentRuntimeService;
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
import com.ls.agent.core.mcp.command.McpToolExecuteCommand;
import com.ls.agent.core.mcp.dto.McpToolExecuteResult;
import com.ls.agent.core.memory.api.MemoryWriteService;
import com.ls.agent.core.memory.command.RecordMemoryCommand;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.skill.api.SkillExecutor;
import com.ls.agent.core.skill.command.SkillExecuteCommand;
import com.ls.agent.core.skill.dto.SkillExecuteResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultAgentRuntimeService implements AgentRuntimeService {

    private static final String CHANNEL_WEB = "WEB";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String MEMORY_TYPE_SUMMARY = "SUMMARY";
    private static final int TITLE_MAX_LENGTH = 60;
    private static final int MAX_AGENT_STEPS = 3;

    private final AgentContextBuilder contextBuilder;
    private final ModelInvokeService modelInvokeService;
    private final SkillExecutor skillExecutor;
    private final McpToolExecutor mcpToolExecutor;
    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final MemoryWriteService memoryWriteService;
    private final ObjectMapper objectMapper;

    public DefaultAgentRuntimeService(
            AgentContextBuilder contextBuilder,
            ModelInvokeService modelInvokeService,
            SkillExecutor skillExecutor,
            McpToolExecutor mcpToolExecutor,
            ConversationMapper conversationMapper,
            ConversationMessageMapper messageMapper,
            MemoryWriteService memoryWriteService,
            ObjectMapper objectMapper
    ) {
        this.contextBuilder = contextBuilder;
        this.modelInvokeService = modelInvokeService;
        this.skillExecutor = skillExecutor;
        this.mcpToolExecutor = mcpToolExecutor;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.memoryWriteService = memoryWriteService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentRunResult run(AgentRunCommand command) {
        validate(command);
        Long conversationId = ensureConversation(command);
        saveMessage(conversationId, command.traceId(), "user", command.userInput(), null);

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

        ModelInvokeResult modelResult = runModelLoop(command, context);
        saveMessage(
                conversationId,
                command.traceId(),
                "assistant",
                modelResult.assistantMessage(),
                modelResult.usage() == null ? null : modelResult.usage().completionTokens()
        );
        saveMemory(command, conversationId, modelResult.assistantMessage());
        return new AgentRunResult(conversationId, modelResult.assistantMessage(), modelResult.usage());
    }

    private ModelInvokeResult runModelLoop(AgentRunCommand command, AgentContextDTO context) {
        List<ModelMessage> messages = new ArrayList<>(context.messages());
        ModelInvokeResult result = null;
        for (int step = 0; step < MAX_AGENT_STEPS; step++) {
            result = modelInvokeService.invoke(new ModelInvokeCommand(
                    context.modelConfigId(),
                    messages,
                    BigDecimal.valueOf(0.7),
                    false
            ));
            ToolCall toolCall = parseToolCall(result.assistantMessage());
            if (toolCall == null) {
                return result;
            }
            String toolOutput = executeTool(command, context, toolCall);
            messages.add(new ModelMessage("assistant", result.assistantMessage()));
            messages.add(new ModelMessage("tool", toolOutput));
        }
        throw new BizException(ErrorCode.AGENT_MAX_STEPS_EXCEEDED, "Agent max steps exceeded");
    }

    private ToolCall parseToolCall(String assistantMessage) {
        if (assistantMessage == null) {
            return null;
        }
        String trimmed = assistantMessage.strip();
        if (trimmed.startsWith("@skill:")) {
            return parseToolCall(trimmed, "@skill:", "skill");
        }
        if (trimmed.startsWith("@mcp:")) {
            return parseToolCall(trimmed, "@mcp:", "mcp");
        }
        return null;
    }

    private ToolCall parseToolCall(String text, String prefix, String type) {
        String payload = text.substring(prefix.length()).strip();
        int jsonStart = payload.indexOf('{');
        if (jsonStart <= 0) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Invalid tool call format");
        }
        String name = payload.substring(0, jsonStart).strip();
        String json = payload.substring(jsonStart);
        try {
            return new ToolCall(type, name, objectMapper.readTree(json));
        } catch (Exception ex) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Invalid tool call arguments");
        }
    }

    private String executeTool(AgentRunCommand command, AgentContextDTO context, ToolCall toolCall) {
        if ("skill".equals(toolCall.type())) {
            if (context.availableSkills().stream().noneMatch(skill -> toolCall.name().equals(skill.code()))) {
                throw new BizException(ErrorCode.REQUEST_INVALID, "Skill is not available in current context");
            }
            SkillExecuteResult result = skillExecutor.execute(new SkillExecuteCommand(
                    command.tenantId(),
                    command.userId(),
                    toolCall.name(),
                    toolCall.arguments()
            ));
            return result.success() ? result.output().toString() : result.errorMessage();
        }
        if (context.availableMcpTools().stream().noneMatch(tool -> toolCall.name().equals(tool.name()))) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "MCP tool is not available in current context");
        }
        McpToolExecuteResult result = mcpToolExecutor.execute(new McpToolExecuteCommand(
                command.tenantId(),
                command.userId(),
                toolCall.name(),
                toolCall.arguments()
        ));
        return result.success() ? result.output().toString() : result.errorMessage();
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

    private Long ensureConversation(AgentRunCommand command) {
        if (command.conversationId() != null) {
            ConversationEntity conversation = conversationMapper.selectById(command.conversationId());
            if (conversation == null
                    || !command.tenantId().equals(conversation.getTenantId())
                    || !command.applicationId().equals(conversation.getApplicationId())
                    || !command.userId().equals(conversation.getUserId())
                    || !command.profileId().equals(conversation.getProfileId())
                    || !STATUS_ACTIVE.equals(conversation.getStatus())) {
                throw new BizException(ErrorCode.REQUEST_INVALID, "Conversation is unavailable");
            }
            return command.conversationId();
        }
        ConversationEntity entity = new ConversationEntity();
        entity.setTenantId(command.tenantId());
        entity.setApplicationId(command.applicationId());
        entity.setUserId(command.userId());
        entity.setProfileId(command.profileId());
        entity.setTitle(titleFrom(command.userInput()));
        entity.setChannel(CHANNEL_WEB);
        entity.setStatus(STATUS_ACTIVE);
        conversationMapper.insert(entity);
        return entity.getId();
    }

    private void saveMessage(Long conversationId, String traceId, String role, String content, Integer tokenCount) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setConversationId(conversationId);
        entity.setTraceId(traceId);
        entity.setRole(role);
        entity.setContent(content == null ? "" : content);
        entity.setTokenCount(tokenCount);
        messageMapper.insert(entity);
    }

    private void saveMemory(AgentRunCommand command, Long conversationId, String assistantMessage) {
        memoryWriteService.record(new RecordMemoryCommand(
                command.tenantId(),
                command.userId(),
                command.applicationId(),
                command.profileId(),
                MEMORY_TYPE_SUMMARY,
                "User: " + command.userInput() + "\nAssistant: " + assistantMessage,
                conversationId
        ));
    }

    private String titleFrom(String userInput) {
        String normalized = userInput == null ? "" : userInput.strip();
        if (normalized.length() <= TITLE_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, TITLE_MAX_LENGTH);
    }

    private void requireNonNull(Object value, String field) {
        if (value == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, field + " is required");
        }
    }

    private record ToolCall(String type, String name, JsonNode arguments) {
    }
}
