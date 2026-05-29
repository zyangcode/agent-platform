package com.ls.agent.core.agent.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.api.ConversationRepository;
import com.ls.agent.core.agent.api.AgentRuntimeService;
import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.dto.AgentRunResult;
import com.ls.agent.core.agent.dto.AgentToolEventDTO;
import com.ls.agent.core.agent.entity.ConversationEntity;
import com.ls.agent.core.agent.entity.ConversationMessageEntity;
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
import com.ls.agent.core.quota.api.TokenUsageService;
import com.ls.agent.core.team.api.TeamEventSink;
import com.ls.agent.core.team.api.TeamRuntimeService;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.FinishTraceSpanCommand;
import com.ls.agent.core.quota.command.RecordTokenUsageCommand;
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceSpanDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DefaultAgentRuntimeService implements AgentRuntimeService {

    private static final String CHANNEL_WEB = "WEB";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String MEMORY_TYPE_SUMMARY = "SUMMARY";
    private static final int TITLE_MAX_LENGTH = 60;
    private static final int MAX_AGENT_STEPS = 3;
    private static final Pattern ARITHMETIC_EXPRESSION_PATTERN = Pattern.compile(
            "(?<![\\w.])-?\\d+(?:\\.\\d+)?(?:\\s*[+\\-*/]\\s*-?\\d+(?:\\.\\d+)?)+"
    );

    private final AgentContextBuilder contextBuilder;
    private final ModelInvokeService modelInvokeService;
    private final SkillExecutor skillExecutor;
    private final McpToolExecutor mcpToolExecutor;
    private final TeamRuntimeService teamRuntimeService;
    private final ConversationRepository conversationRepository;
    private final MemoryWriteService memoryWriteService;
    private final TraceService traceService;
    private final TokenUsageService tokenUsageService;
    private final ObjectMapper objectMapper;

    public DefaultAgentRuntimeService(
            AgentContextBuilder contextBuilder,
            ModelInvokeService modelInvokeService,
            SkillExecutor skillExecutor,
            McpToolExecutor mcpToolExecutor,
            TeamRuntimeService teamRuntimeService,
            ConversationRepository conversationRepository,
            MemoryWriteService memoryWriteService,
            TraceService traceService,
            TokenUsageService tokenUsageService,
            ObjectMapper objectMapper
    ) {
        this.contextBuilder = contextBuilder;
        this.modelInvokeService = modelInvokeService;
        this.skillExecutor = skillExecutor;
        this.mcpToolExecutor = mcpToolExecutor;
        this.teamRuntimeService = teamRuntimeService;
        this.conversationRepository = conversationRepository;
        this.memoryWriteService = memoryWriteService;
        this.traceService = traceService;
        this.tokenUsageService = tokenUsageService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentRunResult run(AgentRunCommand command) {
        return run(command, null);
    }

    @Override
    public AgentRunResult run(AgentRunCommand command, TeamEventSink teamEventSink) {
        validate(command);
        TraceSpanDTO runSpan = safeStartSpan(command.traceId(), null, "agent_runtime.run", "AGENT",
                objectMapper.createObjectNode().put("profileId", command.profileId()));
        try {
            Long conversationId = ensureConversation(command);
            AgentContextDTO context = buildContext(command, conversationId, runSpan == null ? null : runSpan.id());
            if (isTeamMode(context)) {
                saveMessage(conversationId, command.traceId(), "user", command.userInput(), null);
                safeFinishSpan(runSpan, "SUCCESS", null, null);
                AgentRunResult result = teamRuntimeService.run(withConversationId(command, conversationId), teamEventSink);
                saveMessage(
                        conversationId,
                        command.traceId(),
                        "assistant",
                        result.assistantMessage(),
                        result.usage() == null ? null : result.usage().completionTokens()
                );
                saveMemory(command, conversationId, result.assistantMessage());
                return result;
            }

            saveMessage(conversationId, command.traceId(), "user", command.userInput(), null);
            List<AgentToolEventDTO> toolEvents = new ArrayList<>();
            ModelInvokeResult modelResult = runModelLoop(command, context, runSpan == null ? null : runSpan.id(), toolEvents);
            saveMessage(
                    conversationId,
                    command.traceId(),
                    "assistant",
                    modelResult.assistantMessage(),
                    modelResult.usage() == null ? null : modelResult.usage().completionTokens()
            );
            saveMemory(command, conversationId, modelResult.assistantMessage());
            safeFinishSpan(runSpan, "SUCCESS", null, null);
            return new AgentRunResult(conversationId, modelResult.assistantMessage(), modelResult.usage(), toolEvents);
        } catch (Exception ex) {
            safeFinishSpan(runSpan, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
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

    private ModelInvokeResult runModelLoop(
            AgentRunCommand command,
            AgentContextDTO context,
            Long parentSpanId,
            List<AgentToolEventDTO> toolEvents
    ) {
        List<ModelMessage> messages = new ArrayList<>(context.messages());
        ToolCall preselectedToolCall = detectDirectToolCall(command.userInput(), context);
        if (preselectedToolCall != null) {
            String toolOutput = executeTool(command, context, preselectedToolCall, parentSpanId, 0);
            recordToolEvents(toolEvents, preselectedToolCall, toolOutput);
            messages.add(new ModelMessage("assistant", formatToolCall(preselectedToolCall)));
            messages.add(new ModelMessage("tool", toolOutput));
        }
        ModelInvokeResult result = null;
        for (int step = 0; step < MAX_AGENT_STEPS; step++) {
            result = invokeModel(command, context, messages, parentSpanId, step + 1);
            ToolCall toolCall = parseToolCall(result.assistantMessage());
            if (toolCall == null) {
                return result;
            }
            String toolOutput = executeTool(command, context, toolCall, parentSpanId, step + 1);
            recordToolEvents(toolEvents, toolCall, toolOutput);
            messages.add(new ModelMessage("assistant", result.assistantMessage()));
            messages.add(new ModelMessage("tool", toolOutput));
        }
        throw new BizException(ErrorCode.AGENT_MAX_STEPS_EXCEEDED, "Agent max steps exceeded");
    }

    private boolean isTeamMode(AgentContextDTO context) {
        return context != null
                && context.profile() != null
                && "TEAM".equalsIgnoreCase(context.profile().executionMode());
    }

    private AgentRunCommand withConversationId(AgentRunCommand command, Long conversationId) {
        if (conversationId.equals(command.conversationId())) {
            return command;
        }
        return new AgentRunCommand(
                command.tenantId(),
                command.userId(),
                command.applicationId(),
                command.profileId(),
                conversationId,
                command.userInput(),
                command.traceId(),
                command.selectedSkillIds(),
                command.selectedMcpToolIds(),
                command.maxContextTokens()
        );
    }

    private ModelInvokeResult invokeModel(
            AgentRunCommand command,
            AgentContextDTO context,
            List<ModelMessage> messages,
            Long parentSpanId,
            int step
    ) {
        TraceSpanDTO span = safeStartSpan(command.traceId(), parentSpanId, "model.invoke", "MODEL",
                objectMapper.createObjectNode()
                        .put("modelConfigId", context.modelConfigId())
                        .put("step", step));
        try {
            ModelInvokeResult result = modelInvokeService.invoke(new ModelInvokeCommand(
                    context.modelConfigId(),
                    messages,
                    BigDecimal.valueOf(0.7),
                    false
            ));
            safeRecordTokenUsage(command, result, span == null ? null : span.id());
            safeFinishSpan(span, "SUCCESS", null, null);
            return result;
        } catch (Exception ex) {
            safeFinishSpan(span, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    private ToolCall detectDirectToolCall(String userInput, AgentContextDTO context) {
        if (userInput == null || userInput.isBlank() || context.availableSkills().isEmpty()) {
            return null;
        }

        String normalized = userInput.toLowerCase(Locale.ROOT);
        if (isSkillAvailable(context, "calculator")) {
            Matcher matcher = ARITHMETIC_EXPRESSION_PATTERN.matcher(userInput);
            if (matcher.find() && looksLikeCalculationRequest(normalized)) {
                return new ToolCall(
                        "skill",
                        "calculator",
                        objectMapper.createObjectNode().put("expression", matcher.group().replaceAll("\\s+", ""))
                );
            }
        }
        if (isSkillAvailable(context, "weather") && looksLikeWeatherRequest(normalized)) {
            String city = extractAfterKeyword(userInput, List.of("天气", "weather in", "weather"));
            if (!city.isBlank()) {
                return new ToolCall("skill", "weather", objectMapper.createObjectNode().put("city", city));
            }
        }
        if (isSkillAvailable(context, "search") && looksLikeSearchRequest(normalized)) {
            return new ToolCall("skill", "search", objectMapper.createObjectNode().put("query", userInput.strip()));
        }
        return null;
    }

    private boolean isSkillAvailable(AgentContextDTO context, String skillCode) {
        return context.availableSkills().stream().anyMatch(skill -> skillCode.equals(skill.code()));
    }

    private boolean looksLikeCalculationRequest(String normalized) {
        return normalized.contains("计算")
                || normalized.contains("算")
                || normalized.contains("等于")
                || normalized.contains("calculate")
                || normalized.contains("what is");
    }

    private boolean looksLikeWeatherRequest(String normalized) {
        return normalized.contains("天气") || normalized.contains("weather");
    }

    private boolean looksLikeSearchRequest(String normalized) {
        return normalized.contains("搜索")
                || normalized.contains("查询")
                || normalized.contains("查一下")
                || normalized.contains("search");
    }

    private String extractAfterKeyword(String text, List<String> keywords) {
        String stripped = text == null ? "" : text.strip();
        String normalized = stripped.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            int index = normalized.indexOf(keyword.toLowerCase(Locale.ROOT));
            if (index >= 0) {
                String candidate = stripped.substring(index + keyword.length())
                        .replace("?", "")
                        .replace("？", "")
                        .replace("怎么样", "")
                        .replace("如何", "")
                        .strip();
                if (!candidate.isBlank()) {
                    return candidate;
                }
            }
        }
        return stripped;
    }

    private String formatToolCall(ToolCall toolCall) {
        return "@" + toolCall.type() + ":" + toolCall.name() + " " + toolCall.arguments();
    }

    private void recordToolEvents(List<AgentToolEventDTO> toolEvents, ToolCall toolCall, String toolOutput) {
        toolEvents.add(new AgentToolEventDTO(
                "action",
                toolCall.type(),
                toolCall.name(),
                formatToolCall(toolCall)
        ));
        toolEvents.add(new AgentToolEventDTO(
                "observation",
                toolCall.type(),
                toolCall.name(),
                toolOutput
        ));
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

    private String executeTool(
            AgentRunCommand command,
            AgentContextDTO context,
            ToolCall toolCall,
            Long parentSpanId,
            int step
    ) {
        TraceSpanDTO span = safeStartSpan(command.traceId(), parentSpanId, "tool.execute", "TOOL",
                objectMapper.createObjectNode()
                        .put("toolType", toolCall.type())
                        .put("toolName", toolCall.name())
                        .put("step", step));
        try {
            String output = doExecuteTool(command, context, toolCall);
            safeFinishSpan(span, "SUCCESS", null, null);
            return output;
        } catch (Exception ex) {
            safeFinishSpan(span, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    private String doExecuteTool(AgentRunCommand command, AgentContextDTO context, ToolCall toolCall) {
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
            ConversationEntity conversation = conversationRepository.findConversationById(command.conversationId());
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
        conversationRepository.insertConversation(entity);
        return entity.getId();
    }

    private void saveMessage(Long conversationId, String traceId, String role, String content, Integer tokenCount) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setConversationId(conversationId);
        entity.setTraceId(traceId);
        entity.setRole(role);
        entity.setContent(content == null ? "" : content);
        entity.setTokenCount(tokenCount);
        conversationRepository.insertMessage(entity);
        conversationRepository.touchConversation(conversationId);
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
            // Trace is diagnostic data; it must not break the agent run.
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
            // Token usage is diagnostic/accounting data in stage 2.1; do not break chat.
        }
    }

    private String errorCode(Exception ex) {
        return ex instanceof BizException bizException ? bizException.getCode() : ErrorCode.INTERNAL_ERROR.getCode();
    }

    private String errorMessage(Exception ex) {
        return ex.getMessage() == null ? "Agent runtime failed" : ex.getMessage();
    }

    private record ToolCall(String type, String name, JsonNode arguments) {
    }
}
