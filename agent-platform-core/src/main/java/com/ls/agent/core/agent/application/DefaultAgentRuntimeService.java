package com.ls.agent.core.agent.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.api.AgentRuntimeService;
import com.ls.agent.core.agent.api.ConversationRepository;
import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.command.PendingToolCallCommand;
import com.ls.agent.core.agent.dto.AgentRunResult;
import com.ls.agent.core.agent.dto.AgentToolEventDTO;
import com.ls.agent.core.agent.entity.ConversationEntity;
import com.ls.agent.core.agent.entity.ConversationMessageEntity;
import com.ls.agent.core.agent.hook.AgentRuntimeHook;
import com.ls.agent.core.agent.hook.FinalAnswerHookContext;
import com.ls.agent.core.agent.hook.ModelHookContext;
import com.ls.agent.core.agent.hook.ToolHookContext;
import com.ls.agent.core.agent.tool.AgentToolCall;
import com.ls.agent.core.agent.tool.AgentToolCallValidator;
import com.ls.agent.core.agent.tool.AgentToolDTO;
import com.ls.agent.core.agent.tool.AgentToolDispatchCommand;
import com.ls.agent.core.agent.tool.AgentToolDispatchResult;
import com.ls.agent.core.agent.tool.AgentToolDispatcher;
import com.ls.agent.core.agent.tool.AgentToolResolver;
import com.ls.agent.core.agent.tool.AgentToolRiskLevel;
import com.ls.agent.core.agent.tool.AgentToolSourceType;
import com.ls.agent.core.agent.tool.AgentToolValidationResult;
import com.ls.agent.core.agent.tool.ToolExecutionPlan;
import com.ls.agent.core.agent.tool.ToolExecutionPlanner;
import com.ls.agent.core.context.api.AgentContextBuilder;
import com.ls.agent.core.context.api.MicroCompactService;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.context.dto.ContextBudgetSnapshotDTO;
import com.ls.agent.core.context.dto.MicroCompactResult;
import com.ls.agent.core.memory.api.MemoryWriteService;
import com.ls.agent.core.memory.application.PreferenceExtractor;
import com.ls.agent.core.memory.command.RecordMemoryCommand;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.api.ModelStreamCallback;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.model.dto.ModelToolCallDTO;
import com.ls.agent.core.model.dto.ModelToolSpecDTO;
import com.ls.agent.core.quota.api.TokenUsageService;
import com.ls.agent.core.quota.command.RecordTokenUsageCommand;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.team.api.TeamEventSink;
import com.ls.agent.core.team.api.TeamRuntimeService;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.FinishTraceSpanCommand;
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceSpanDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

@Service
public class DefaultAgentRuntimeService implements AgentRuntimeService {

    private static final String CHANNEL_WEB = "WEB";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String MEMORY_TYPE_SUMMARY = "SUMMARY";
    private static final int TITLE_MAX_LENGTH = 60;
    private static final int MAX_AGENT_STEPS = 6;
    private static final int MAX_TOOL_CALLS = 8;
    private static final int MAX_PARALLEL_TOOLS = 4;

    private final AgentContextBuilder contextBuilder;
    private final MicroCompactService microCompactService;
    private final ModelInvokeService modelInvokeService;
    private final AgentToolResolver agentToolResolver;
    private final AgentToolDispatcher agentToolDispatcher;
    private final AgentToolCallValidator agentToolCallValidator;
    private final ToolExecutionPlanner toolExecutionPlanner;
    private final TeamRuntimeService teamRuntimeService;
    private final SingleAgentFinalResponseSynthesizer finalResponseSynthesizer;
    private final ConversationRepository conversationRepository;
    private final MemoryWriteService memoryWriteService;
    private final PreferenceExtractor preferenceExtractor;
    private final TraceService traceService;
    private final TokenUsageService tokenUsageService;
    private final ObjectMapper objectMapper;
    private final List<AgentRuntimeHook> runtimeHooks;

    public DefaultAgentRuntimeService(
            AgentContextBuilder contextBuilder,
            MicroCompactService microCompactService,
            ModelInvokeService modelInvokeService,
            AgentToolResolver agentToolResolver,
            AgentToolDispatcher agentToolDispatcher,
            AgentToolCallValidator agentToolCallValidator,
            ToolExecutionPlanner toolExecutionPlanner,
            TeamRuntimeService teamRuntimeService,
            SingleAgentFinalResponseSynthesizer finalResponseSynthesizer,
            ConversationRepository conversationRepository,
            MemoryWriteService memoryWriteService,
            PreferenceExtractor preferenceExtractor,
            TraceService traceService,
            TokenUsageService tokenUsageService,
            ObjectMapper objectMapper,
            List<AgentRuntimeHook> runtimeHooks
    ) {
        this.contextBuilder = contextBuilder;
        this.microCompactService = microCompactService;
        this.modelInvokeService = modelInvokeService;
        this.agentToolResolver = agentToolResolver;
        this.agentToolDispatcher = agentToolDispatcher;
        this.agentToolCallValidator = agentToolCallValidator;
        this.toolExecutionPlanner = toolExecutionPlanner;
        this.teamRuntimeService = teamRuntimeService;
        this.finalResponseSynthesizer = finalResponseSynthesizer;
        this.conversationRepository = conversationRepository;
        this.memoryWriteService = memoryWriteService;
        this.preferenceExtractor = preferenceExtractor;
        this.traceService = traceService;
        this.tokenUsageService = tokenUsageService;
        this.objectMapper = objectMapper;
        this.runtimeHooks = runtimeHooks == null ? List.of() : List.copyOf(runtimeHooks);
    }

    @Override
    public AgentRunResult run(AgentRunCommand command) {
        return run(command, null, null);
    }

    @Override
    public AgentRunResult run(AgentRunCommand command, TeamEventSink teamEventSink) {
        return run(command, teamEventSink, null);
    }

    /**
     * 执行智能体运行的核心方法。
     * 支持团队模式、单智能体 ReAct 循环、工具调用以及流式输出。
     *
     * @param command 运行命令，包含用户输入、配置 ID 等
     * @param teamEventSink 团队事件接收器，用于团队协作模式下的进度同步
     * @param streamCallback 模型流式回调，用于实现打字机效果
     * @return 智能体运行结果，包含最终回复、Token 消耗及工具执行事件
     */
    @Override
    public AgentRunResult run(AgentRunCommand command, TeamEventSink teamEventSink, ModelStreamCallback streamCallback) {
        // 1. 基础参数校验
        validate(command);
        
        // 2. 开启链路追踪：记录 Agent 运行的总 Span
        TraceSpanDTO runSpan = safeStartSpan(command.traceId(), null, "agent_runtime.run", "AGENT",
                objectMapper.createObjectNode().put("profileId", command.profileId()));
        
        try {
            // 3. 确保会话存在（复用已有会话或创建新会话），并校验会话归属
            Long conversationId = ensureConversation(command);
            
            // 4. 构建 Agent 执行上下文（包含记忆、提示词、可用工具等）
            AgentContextDTO context = buildContext(command, conversationId, spanId(runSpan));
            
            // 5. 判断是否为“团队模式” (Team Mode)
            if (isTeamMode(command, context)) {
                // A. 保存用户的原始输入消息
                saveMessage(conversationId, command.traceId(), "user", command.userInput(), null);
                // B. 标记当前 Span 为成功（因为后续交给 TeamRuntime 处理）
                safeFinishSpan(runSpan, "SUCCESS", null, null);
                // C. 转发给团队执行引擎处理
                AgentRunResult result = teamRuntimeService.run(withConversationId(command, conversationId), teamEventSink);
                // D. 保存团队生成的最终回复消息
                saveMessage(
                        conversationId,
                        command.traceId(),
                        "assistant",
                        result.assistantMessage(),
                        result.usage() == null ? null : result.usage().completionTokens()
                );
                // E. 记录/更新长期记忆
                saveMemory(command, context, spanId(runSpan), conversationId, result.assistantMessage());
                return result;
            }

            // 6. 单智能体模式：保存用户消息
            saveMessage(conversationId, command.traceId(), "user", command.userInput(), null);
            
            // 7. 进入单智能体 ReAct 循环（思考 -> 调工具 -> 观察 -> 再思考）
            List<AgentToolEventDTO> toolEvents = new ArrayList<>();
            ModelInvokeResult modelResult = runModelLoop(command, context, spanId(runSpan), toolEvents, streamCallback);
            
            // 8. 构建最终回答（可能包含后处理逻辑）
            String finalAssistantMessage = buildFinalAnswer(command, spanId(runSpan), modelResult.assistantMessage());
            
            // 9. 保存 AI 的最终回复消息
            saveMessage(
                    conversationId,
                    command.traceId(),
                    "assistant",
                    finalAssistantMessage,
                    modelResult.usage() == null ? null : modelResult.usage().completionTokens()
            );
            
            // 10. 记录/更新长期记忆
            saveMemory(command, context, spanId(runSpan), conversationId, finalAssistantMessage, toolEvents);
            
            // 11. 标记 Span 成功并返回结果
            safeFinishSpan(runSpan, "SUCCESS", null, null);
            return new AgentRunResult(conversationId, finalAssistantMessage, modelResult.usage(), toolEvents, context.ragSearchResults());
            
        } catch (Exception ex) {
            // 12. 异常处理：标记 Span 失败并向上抛出
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
                    command.selectedMcpToolIds(),
                    command.traceId(),
                    spanId(span)
            ));
            recordContextBudgetSnapshot(command, spanId(span), context);
            safeFinishSpan(span, "SUCCESS", null, null);
            return context;
        } catch (Exception ex) {
            safeFinishSpan(span, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    /**
     * 运行智能体的 ReAct (Reasoning and Acting) 核心循环。
     * 该循环允许智能体多次调用模型进行思考，并根据模型的要求执行工具调用。
     *
     * @param command 运行命令
     * @param context Agent 执行上下文
     * @param parentSpanId 父链路跟踪 ID
     * @param toolEvents 用于记录工具执行事件的列表
     * @param streamCallback 模型流式回调
     * @return 最终的模型调用结果
     */
    private ModelInvokeResult runModelLoop(
            AgentRunCommand command,
            AgentContextDTO context,
            Long parentSpanId,
            List<AgentToolEventDTO> toolEvents,
            ModelStreamCallback streamCallback
    ) {
        // 1. 初始化对话历史消息列表
        List<ModelMessage> messages = new ArrayList<>(context.apiMessages());
        // 2. 解析并获取当前上下文下所有可用的工具列表
        List<AgentToolDTO> availableTools = safeTools(agentToolResolver.resolve(context));
        
        // 3. 检查是否存在待处理的工具调用（例如需要人工确认的高风险工具）
        ModelInvokeResult resumedResult = resumePendingToolCallIfPresent(
                command,
                context,
                messages,
                availableTools,
                parentSpanId,
                toolEvents,
                streamCallback
        );
        if (resumedResult != null) {
            // 如果恢复了待处理调用并直接得到了结果，则直接返回
            return resumedResult;
        }

        int toolCallCount = 0; // 累计工具调用次数
        boolean repaired = false; // 标记是否进行过指令修复
        Consumer<String> progress = command.progressCallback(); // 获取进度回调

        // 4. 开始 ReAct 循环，最大步数由 MAX_AGENT_STEPS 限制
        for (int step = 0; step < MAX_AGENT_STEPS; step++) {
            // A. 消息压缩：确保消息历史不超出模型的 Token 预算
            messages = compactMessages(command, parentSpanId, messages);
            
            // B. 准备流式缓冲区：在 Agent 模式下，中间思考过程通常不直接流向前端，除非是最终回复
            BufferedStreamCallback bufferedStream = streamCallback == null
                    ? null
                    : new BufferedStreamCallback();
            
            // C. 推送进度状态
            if (progress != null) {
                progress.accept(step == 0 ? "正在分析..." : "正在进一步推理...");
            }

            // D. 调用模型进行推理
            ModelInvokeResult result = invokeModel(
                    command,
                    context,
                    messages,
                    availableTools,
                    parentSpanId,
                    step + 1,
                    bufferedStream
            );

            // E. 解析模型回复中的工具调用请求 (如 @skill:xxx)
            ToolRequestBatch toolRequestBatch;
            try {
                toolRequestBatch = toolRequestBatchFrom(result);
            } catch (Exception ex) {
                // 如果解析失败，记录错误并尝试进行兜底直接回答
                messages.add(new ModelMessage("assistant", result.assistantMessage()));
                messages.add(new ModelMessage("tool", "[tool call parse error] " + errorMessage(ex)));
                return fallbackDirectAnswer(command, context, messages, parentSpanId, streamCallback);
            }

            // F. 如果模型没有请求调用任何工具
            if (toolRequestBatch.isEmpty()) {
                // 尝试进行指令修复：有时模型可能由于指令遵循能力问题，没有按正确格式输出工具调用
                if (!repaired) {
                    ToolRequestBatch repairedToolRequestBatch = repairMissingToolRequestBatch(
                            command,
                            result.assistantMessage(),
                            availableTools
                    );
                    if (!repairedToolRequestBatch.isEmpty()) {
                        repaired = true;
                        messages.add(new ModelMessage("assistant", result.assistantMessage()));
                        // 执行修复后的工具调用
                        for (ToolExecutionResult toolResult : executeToolBatch(command, repairedToolRequestBatch, availableTools, parentSpanId, step + 1)) {
                            recordToolEvents(toolEvents, toolResult.toolCall(), toolResult.output());
                            messages.add(new ModelMessage("tool", compactMessage(command, parentSpanId, "tool", toolResult.output())));
                        }
                        toolCallCount += repairedToolRequestBatch.toolCalls().size();
                        // 检查工具调用上限
                        if (toolCallCount >= MAX_TOOL_CALLS) {
                            return fallbackDirectAnswer(command, context, messages, parentSpanId, streamCallback);
                        }
                        continue; // 继续下一轮循环，让模型观察工具执行结果
                    }
                }
                
                // 确定没有工具调用且无需修复，则认为这是最终回答
                if (streamCallback == null) {
                    return result;
                }
                // 如果开启了流式输出，将缓冲区中的最终内容刷给前端
                flushBufferedVisibleAnswer(streamCallback, result.assistantMessage(), bufferedStream);
                return result;
            }

            // G. 模型请求了工具调用：记录 Assistant 的回复
            messages.add(new ModelMessage("assistant", result.assistantMessage()));
            
            // H. 校验工具调用的合法性（权限、参数等）
            List<AgentToolValidationResult> validations = validateToolRequestBatch(
                    command,
                    parentSpanId,
                    toolRequestBatch,
                    availableTools
            );
            
            // I. 如果存在非法调用，将错误信息反馈给模型，尝试让其修正
            if (hasInvalidToolCall(validations)) {
                for (AgentToolValidationResult validation : validations) {
                    if (!validation.valid()) {
                        messages.add(new ModelMessage("tool", compactMessage(command, parentSpanId, "tool", validation.observation())));
                    }
                }
                return fallbackDirectAnswer(command, context, messages, parentSpanId, streamCallback);
            }

            repaired = true;
            // J. 推送进度状态
            if (progress != null) {
                progress.accept("正在执行工具调用...");
            }
            
            // K. 批量执行工具调用
            for (ToolExecutionResult toolResult : executeToolBatch(command, toolRequestBatch, availableTools, parentSpanId, step + 1)) {
                // 记录执行事件（用于前端展示执行过程）
                recordToolEvents(toolEvents, toolResult.toolCall(), toolResult.output());
                // 将工具输出作为观察结果 (Observation) 加入对话历史
                messages.add(new ModelMessage("tool", compactMessage(command, parentSpanId, "tool", toolResult.output())));
            }

            // L. 统计调用次数并检查是否超出上限
            toolCallCount += toolRequestBatch.toolCalls().size();
            if (toolCallCount >= MAX_TOOL_CALLS) {
                // 超出上限强制结束，进行兜底回答
                return fallbackDirectAnswer(command, context, messages, parentSpanId, streamCallback);
            }
            // 继续下一轮循环，让模型根据工具执行结果进行下一步思考
        }

        // 5. 达到最大步数仍未结束，推送进度并进行最终兜底回复
        if (progress != null) {
            progress.accept("正在生成回复...");
        }
        return fallbackDirectAnswer(command, context, messages, parentSpanId, streamCallback);
    }

    private void flushBufferedVisibleAnswer(
            ModelStreamCallback streamCallback,
            String assistantMessage,
            BufferedStreamCallback bufferedStream
    ) {
        if (streamCallback == null) {
            return;
        }
        if (bufferedStream != null && !bufferedStream.tokens().isEmpty()) {
            for (String token : bufferedStream.tokens()) {
                streamCallback.onToken(token);
            }
            return;
        }
        if (assistantMessage != null && !assistantMessage.isBlank()) {
            streamCallback.onToken(assistantMessage);
        }
    }

    private List<AgentToolDTO> safeTools(List<AgentToolDTO> availableTools) {
        return availableTools == null ? List.of() : availableTools;
    }

    private List<ModelToolSpecDTO> modelToolSpecs(List<AgentToolDTO> availableTools) {
        if (availableTools == null || availableTools.isEmpty()) {
            return List.of();
        }
        return availableTools.stream()
                .filter(tool -> tool != null)
                .map(tool -> new ModelToolSpecDTO(
                        tool.sourceType().name(),
                        tool.name(),
                        tool.description(),
                        tool.parameterSchema()
                ))
                .toList();
    }

    private ModelInvokeResult resumePendingToolCallIfPresent(
            AgentRunCommand command,
            AgentContextDTO context,
            List<ModelMessage> messages,
            List<AgentToolDTO> availableTools,
            Long parentSpanId,
            List<AgentToolEventDTO> toolEvents,
            ModelStreamCallback streamCallback
    ) {
        PendingToolCallCommand pendingToolCall = command.pendingToolCall();
        if (pendingToolCall == null) {
            return null;
        }
        AgentToolCall call = new AgentToolCall(
                pendingToolCall.sourceType(),
                pendingToolCall.toolName(),
                pendingToolCall.arguments()
        );
        if (!command.confirmedToolKeys().contains(toolKey(call))) {
            messages.add(new ModelMessage("tool", "[tool confirm required] "
                    + call.sourceType().name().toLowerCase() + ":" + call.toolName() + " risk=HIGH"));
            return fallbackDirectAnswer(command, context, messages, parentSpanId, streamCallback);
        }
        // 已确认：只把白名单带入循环，不强制执行工具。
        // 模型进入 ReAct 循环后自己判断何时调用，doExecuteTool 检查白名单自动通过。
        return null;
    }

    private ModelInvokeResult fallbackDirectAnswer(
            AgentRunCommand command,
            AgentContextDTO context,
            List<ModelMessage> accumulatedMessages,
            Long parentSpanId,
            ModelStreamCallback streamCallback
    ) {
        List<ModelMessage> fallbackMessages = finalResponseSynthesizer.fallbackMessages(accumulatedMessages);

        try {
            return invokeModel(command, context, fallbackMessages, List.of(), parentSpanId, MAX_AGENT_STEPS + 1, streamCallback);
        } catch (Exception ex) {
            return new ModelInvokeResult(
                    context.modelConfigId(),
                    null,
                    null,
                    null,
                    "Sorry, I cannot answer this request right now. Please try again with more details.",
                    null
            );
        }
    }

    private boolean isTeamMode(AgentRunCommand command, AgentContextDTO context) {
        return command != null && "team".equalsIgnoreCase(command.agentMode())
                || context != null
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
                command.maxContextTokens(),
                command.agentMode(),
                command.confirmedToolKeys(),
                command.pendingToolCall(),
                command.progressCallback()
        );
    }

    private ModelInvokeResult invokeModel(
            AgentRunCommand command,
            AgentContextDTO context,
            List<ModelMessage> messages,
            Long parentSpanId,
            int step
    ) {
        return invokeModel(command, context, messages, List.of(), parentSpanId, step, null);
    }

    private ModelInvokeResult invokeModel(
            AgentRunCommand command,
            AgentContextDTO context,
            List<ModelMessage> messages,
            List<AgentToolDTO> availableTools,
            Long parentSpanId,
            int step,
            ModelStreamCallback streamCallback
    ) {
        ObjectNode attributes = objectMapper.createObjectNode()
                .put("modelConfigId", context.modelConfigId())
                .put("step", step)
                .put("messageCount", messages == null ? 0 : messages.size())
                .put("toolSpecCount", availableTools == null ? 0 : availableTools.size())
                .put("stream", streamCallback != null);
        TraceSpanDTO span = safeStartSpan(command.traceId(), parentSpanId, "model.invoke", "MODEL", attributes);
        ModelHookContext hookContext = new ModelHookContext(
                command.traceId(),
                command.tenantId(),
                command.applicationId(),
                command.userId(),
                command.profileId(),
                context.modelConfigId(),
                step,
                messages == null ? 0 : messages.size(),
                availableTools == null ? 0 : availableTools.size(),
                streamCallback != null
        );
        try {
            ModelInvokeCommand invokeCommand = new ModelInvokeCommand(
                    context.modelConfigId(),
                    messages,
                    BigDecimal.valueOf(0.7),
                    false,
                    modelToolSpecs(availableTools)
            );
            notifyPreModelCall(hookContext);
            ModelInvokeResult result = streamCallback == null
                    ? modelInvokeService.invoke(invokeCommand)
                    : modelInvokeService.invoke(invokeCommand, streamCallback);
            notifyPostModelCall(hookContext, result, null);
            recordModelSpanResult(attributes, result);
            safeRecordTokenUsage(command, result, spanId(span));
            safeFinishSpan(span, "SUCCESS", null, null, attributes);
            return result;
        } catch (Exception ex) {
            notifyPostModelCall(hookContext, null, ex);
            safeFinishSpan(span, "FAILED", errorCode(ex), errorMessage(ex), attributes);
            throw ex;
        }
    }

    private ToolRequestBatch parseToolRequestBatch(String assistantMessage) {
        if (assistantMessage == null) {
            return ToolRequestBatch.empty();
        }
        String trimmed = assistantMessage.strip();
        if (trimmed.isEmpty()) {
            return ToolRequestBatch.empty();
        }
        List<ToolCall> toolCalls = new ArrayList<>();
        for (String line : trimmed.split("\\R")) {
            String candidate = line.strip();
            if (candidate.isEmpty()) {
                continue;
            }
            ToolCall toolCall = parseToolCallLine(candidate);
            if (toolCall == null) {
                if (toolCalls.isEmpty()) {
                    return ToolRequestBatch.empty();
                }
                throw new BizException(ErrorCode.REQUEST_INVALID, "Invalid tool call batch");
            }
            toolCalls.add(toolCall);
        }
        return new ToolRequestBatch(toolCalls);
    }

    private ToolRequestBatch toolRequestBatchFrom(ModelInvokeResult result) {
        if (result != null && result.toolCalls() != null && !result.toolCalls().isEmpty()) {
            return new ToolRequestBatch(result.toolCalls().stream()
                    .map(this::toolCallFromModel)
                    .toList());
        }
        return parseToolRequestBatch(result == null ? null : result.assistantMessage());
    }

    private ToolCall toolCallFromModel(ModelToolCallDTO call) {
        AgentToolSourceType sourceType = AgentToolSourceType.valueOf(call.sourceType());
        return new ToolCall(sourceType, call.name(), call.arguments());
    }

    private ToolRequestBatch repairMissingToolRequestBatch(
            AgentRunCommand command,
            String assistantMessage,
            List<AgentToolDTO> availableTools
    ) {
        if (!looksLikeWeatherToolRequired(command.userInput(), assistantMessage)) {
            return ToolRequestBatch.empty();
        }
        String city = extractWeatherCity(command.userInput());
        if (city.isBlank()) {
            return ToolRequestBatch.empty();
        }
        if (hasTool(availableTools, AgentToolSourceType.MCP, "weather.current")) {
            return new ToolRequestBatch(List.of(new ToolCall(
                    AgentToolSourceType.MCP,
                    "weather.current",
                    objectMapper.createObjectNode().put("city", city)
            )));
        }
        if (!hasTool(availableTools, AgentToolSourceType.SKILL, "weather")) {
            return ToolRequestBatch.empty();
        }
        return new ToolRequestBatch(List.of(new ToolCall(
                AgentToolSourceType.SKILL,
                "weather",
                objectMapper.createObjectNode().put("city", city)
        )));
    }

    private boolean looksLikeWeatherToolRequired(String userInput, String assistantMessage) {
        return looksLikeExplicitWeatherRequest(userInput) || looksLikeWeatherLookupPromise(assistantMessage);
    }

    private boolean looksLikeExplicitWeatherRequest(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return false;
        }
        String normalized = userInput.toLowerCase(Locale.ROOT);
        boolean weatherIntent = normalized.contains("天气") || normalized.contains("weather");
        boolean lookupIntent = normalized.contains("查")
                || normalized.contains("查询")
                || normalized.contains("看看")
                || normalized.contains("获取")
                || normalized.contains("后回答")
                || normalized.contains("lookup")
                || normalized.contains("check")
                || normalized.contains("query");
        boolean decisionNeedsWeather = normalized.contains("适合")
                && (normalized.contains("打篮球")
                || normalized.contains("运动")
                || normalized.contains("出门")
                || normalized.contains("跑步"));
        return weatherIntent && (lookupIntent || decisionNeedsWeather);
    }

    private boolean hasTool(List<AgentToolDTO> availableTools, AgentToolSourceType sourceType, String toolName) {
        if (availableTools == null || availableTools.isEmpty()) {
            return false;
        }
        return availableTools.stream()
                .anyMatch(tool -> tool != null
                        && sourceType.equals(tool.sourceType())
                        && toolName.equals(tool.name()));
    }

    private boolean looksLikeWeatherLookupPromise(String assistantMessage) {
        if (assistantMessage == null || assistantMessage.isBlank()) {
            return false;
        }
        String normalized = assistantMessage.toLowerCase(Locale.ROOT);
        boolean weatherIntent = normalized.contains("天气") || normalized.contains("weather");
        boolean lookupPromise = normalized.contains("查")
                || normalized.contains("查询")
                || normalized.contains("马上")
                || normalized.contains("让我")
                || normalized.contains("lookup")
                || normalized.contains("check")
                || normalized.contains("query");
        return weatherIntent && lookupPromise;
    }

    private String extractWeatherCity(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return "";
        }
        String normalized = userInput.strip()
                .replaceAll("[，。！？、,.!?;；:：\\s]+$", "")
                .replaceAll("^(城市|地区|地点|位置)[:：\\s]*", "");
        if (normalized.length() <= 12 && !normalized.isBlank()) {
            return normalized;
        }
        java.util.regex.Matcher cityBeforeTimeMatcher = java.util.regex.Pattern
                .compile("([\\p{IsHan}A-Za-z]{2,20})(?:今天|明天|现在|当前)")
                .matcher(normalized);
        if (cityBeforeTimeMatcher.find()) {
            return cityBeforeTimeMatcher.group(1);
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("([\\p{IsHan}A-Za-z]{2,20})(?:今天|明天|现在|当前|的)?天气")
                .matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private ToolCall parseToolCallLine(String text) {
        if (text.startsWith("@skill:")) {
            return parseToolCall(text, "@skill:", AgentToolSourceType.SKILL);
        }
        if (text.startsWith("@mcp:")) {
            return parseToolCall(text, "@mcp:", AgentToolSourceType.MCP);
        }
        return null;
    }

    private ToolCall parseToolCall(String text, String prefix, AgentToolSourceType sourceType) {
        String payload = text.substring(prefix.length()).strip();
        int jsonStart = payload.indexOf('{');
        if (jsonStart <= 0) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Invalid tool call format");
        }
        String name = payload.substring(0, jsonStart).strip();
        String json = payload.substring(jsonStart);
        try {
            return new ToolCall(sourceType, name, objectMapper.readTree(json));
        } catch (Exception ex) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Invalid tool call arguments");
        }
    }

    private String executeTool(
            AgentRunCommand command,
            ToolCall toolCall,
            List<AgentToolDTO> availableTools,
            Long parentSpanId,
            int step
    ) {
        AgentToolValidationResult validation = validateToolCall(toolCall, availableTools);
        ObjectNode attributes = toolSpanAttributes(toolCall, validation, step);
        TraceSpanDTO span = safeStartSpan(command.traceId(), parentSpanId, "tool.execute", "TOOL", attributes);
        ToolHookContext hookContext = toolHookContext(command, toolCall, validation, step, attributes);
        try {
            notifyPreToolCall(hookContext);
            String output = doExecuteTool(command, validation);
            notifyPostToolCall(hookContext, output, null);
            recordToolSpanResult(attributes, output);
            safeFinishSpan(span, "SUCCESS", null, null, attributes);
            return output;
        } catch (Exception ex) {
            notifyPostToolCall(hookContext, null, ex);
            attributes.put("success", false);
            safeFinishSpan(span, "FAILED", errorCode(ex), errorMessage(ex), attributes);
            throw ex;
        }
    }

    private ObjectNode toolSpanAttributes(ToolCall toolCall, AgentToolValidationResult validation, int step) {
        ObjectNode attributes = objectMapper.createObjectNode()
                .put("toolType", toolCall.sourceType().name())
                .put("toolName", toolCall.name())
                .put("step", step)
                .put("argumentsSummary", summarizeJson(toolCall.arguments()));
        if (validation != null && validation.tool() != null) {
            attributes.put("riskLevel", validation.tool().riskLevel().name());
            attributes.put("readOnly", validation.tool().readOnly());
            attributes.set("resourceKeys", objectMapper.valueToTree(validation.tool().resourceKeys()));
        }
        return attributes;
    }

    private List<ToolExecutionResult> executeToolBatch(
            AgentRunCommand command,
            ToolRequestBatch toolRequestBatch,
            List<AgentToolDTO> availableTools,
            Long parentSpanId,
            int step
    ) {
        List<ToolExecutionPlan.Item> items = new ArrayList<>();
        for (ToolCall toolCall : toolRequestBatch.toolCalls()) {
            AgentToolValidationResult validation = validateToolCall(toolCall, availableTools);
            items.add(new ToolExecutionPlan.Item(
                    toolCall.sourceType(),
                    toolCall.name(),
                    toolCall.arguments(),
                    validation.tool()
            ));
        }
        ToolExecutionPlan plan = planToolExecution(command, parentSpanId, items);
        List<ToolExecutionResult> results = new ArrayList<>();
        for (ToolExecutionPlan.Group group : plan.groups()) {
            if (group.parallel()) {
                results.addAll(executeParallelToolGroup(command, group, availableTools, parentSpanId, step));
            } else {
                for (ToolExecutionPlan.Item item : group.items()) {
                    ToolCall toolCall = new ToolCall(item.sourceType(), item.toolName(), item.arguments());
                    results.add(new ToolExecutionResult(
                            toolCall,
                            executeTool(command, toolCall, availableTools, parentSpanId, step)
                    ));
                }
            }
        }
        return results;
    }

    private ToolExecutionPlan planToolExecution(
            AgentRunCommand command,
            Long parentSpanId,
            List<ToolExecutionPlan.Item> items
    ) {
        ObjectNode attributes = objectMapper.createObjectNode()
                .put("toolCount", items == null ? 0 : items.size())
                .put("maxParallelTools", MAX_PARALLEL_TOOLS);
        TraceSpanDTO span = safeStartSpan(command.traceId(), parentSpanId, "tool.plan", "TOOL", attributes);
        try {
            ToolExecutionPlan plan = toolExecutionPlanner.plan(items, MAX_PARALLEL_TOOLS);
            attributes.put("groupCount", plan.groups().size());
            attributes.put("parallelGroupCount", plan.groups().stream()
                    .filter(ToolExecutionPlan.Group::parallel)
                    .count());
            safeFinishSpan(span, "SUCCESS", null, null);
            return plan;
        } catch (Exception ex) {
            safeFinishSpan(span, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    private List<AgentToolValidationResult> validateToolRequestBatch(
            AgentRunCommand command,
            Long parentSpanId,
            ToolRequestBatch toolRequestBatch,
            List<AgentToolDTO> availableTools
    ) {
        TraceSpanDTO span = null;
        try {
            List<AgentToolValidationResult> validations = toolRequestBatch.toolCalls().stream()
                    .map(toolCall -> validateToolCall(toolCall, availableTools))
                    .toList();
            ObjectNode attributes = objectMapper.createObjectNode()
                    .put("batchSize", validations.size())
                    .put("invalidCount", validations.stream()
                            .filter(validation -> !validation.valid())
                            .count());
            attributes.set("toolKeys", objectMapper.valueToTree(validations.stream()
                    .map(validation -> toolKey(validation.call()))
                    .toList()));
            span = safeStartSpan(command.traceId(), parentSpanId, "tool.validate", "TOOL", attributes);
            safeFinishSpan(span, "SUCCESS", null, null);
            return validations;
        } catch (Exception ex) {
            safeFinishSpan(span, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    private boolean hasInvalidToolCall(List<AgentToolValidationResult> validations) {
        return validations.stream().anyMatch(validation -> !validation.valid());
    }

    private List<ToolExecutionResult> executeParallelToolGroup(
            AgentRunCommand command,
            ToolExecutionPlan.Group group,
            List<AgentToolDTO> availableTools,
            Long parentSpanId,
            int step
    ) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(MAX_PARALLEL_TOOLS, group.items().size()));
        try {
            List<Callable<ToolExecutionResult>> tasks = group.items().stream()
                    .map(item -> (Callable<ToolExecutionResult>) () -> {
                        ToolCall toolCall = new ToolCall(item.sourceType(), item.toolName(), item.arguments());
                        return new ToolExecutionResult(
                                toolCall,
                                executeTool(command, toolCall, availableTools, parentSpanId, step)
                        );
                    })
                    .toList();
            List<Future<ToolExecutionResult>> futures = executor.invokeAll(tasks);
            List<ToolExecutionResult> results = new ArrayList<>(futures.size());
            for (Future<ToolExecutionResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Tool batch execution interrupted");
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, errorMessage(ex));
        } finally {
            executor.shutdownNow();
        }
    }

    private String doExecuteTool(AgentRunCommand command, AgentToolValidationResult validation) {
        if (!validation.valid()) {
            return validation.observation();
        }
        if (validation.tool().riskLevel() == AgentToolRiskLevel.HIGH && !isConfirmed(command, validation)) {
            return "[tool confirm required] "
                    + validation.call().sourceType().name().toLowerCase()
                    + ":" + validation.call().toolName()
                    + " risk=HIGH";
        }
        AgentToolDispatchResult result = agentToolDispatcher.dispatch(new AgentToolDispatchCommand(
                command.tenantId(),
                command.userId(),
                validation.call().toolName(),
                validation.call().sourceType(),
                validation.call().arguments()
        ));
        if (!result.success()) {
            String errorMessage = result.errorMessage() == null || result.errorMessage().isBlank()
                    ? validation.call().toolName()
                    : result.errorMessage();
            return errorMessage.startsWith("[tool failed]")
                    ? errorMessage
                    : "[tool failed] " + errorMessage;
        }
        return result.output() == null || result.output().isNull() ? "[empty]" : result.output().toString();
    }

    private boolean isConfirmed(AgentRunCommand command, AgentToolValidationResult validation) {
        if (command.confirmedToolKeys() == null || command.confirmedToolKeys().isEmpty()) {
            return false;
        }
        return command.confirmedToolKeys().contains(toolKey(validation.call()));
    }

    private String toolKey(AgentToolCall call) {
        return call.sourceType().name().toLowerCase() + ":" + call.toolName();
    }

    private AgentToolValidationResult validateToolCall(ToolCall toolCall, List<AgentToolDTO> availableTools) {
        AgentToolCall call = new AgentToolCall(toolCall.sourceType(), toolCall.name(), toolCall.arguments());
        return agentToolCallValidator.validate(call, availableTools);
    }

    private String formatToolCall(ToolCall toolCall) {
        return "@" + toolCall.sourceType().name().toLowerCase() + ":" + toolCall.name() + " " + toolCall.arguments();
    }

    private void recordToolEvents(List<AgentToolEventDTO> toolEvents, ToolCall toolCall, String toolOutput) {
        toolEvents.add(new AgentToolEventDTO(
                "action",
                toolCall.sourceType().name().toLowerCase(),
                toolCall.name(),
                formatToolCall(toolCall)
        ));
        toolEvents.add(new AgentToolEventDTO(
                toolEventType(toolOutput),
                toolCall.sourceType().name().toLowerCase(),
                toolCall.name(),
                toolOutput,
                toolEventMetadata(toolCall, toolOutput)
        ));
    }

    private String toolEventType(String toolOutput) {
        return toolOutput != null && toolOutput.startsWith("[tool confirm required]")
                ? "tool_confirm_required"
                : "observation";
    }

    private Map<String, Object> toolEventMetadata(ToolCall toolCall, String toolOutput) {
        if (!"tool_confirm_required".equals(toolEventType(toolOutput))) {
            return Map.of();
        }
        String sourceType = toolCall.sourceType().name().toLowerCase();
        return Map.of(
                "toolType", sourceType,
                "toolName", toolCall.name(),
                "toolKey", sourceType + ":" + toolCall.name(),
                "pendingToolCall", Map.of(
                        "sourceType", toolCall.sourceType().name(),
                        "toolName", toolCall.name(),
                        "arguments", toolCall.arguments()
                )
        );
    }

    private void validate(AgentRunCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "command is required");
        }
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

    private void saveMemory(AgentRunCommand command, AgentContextDTO context, Long parentSpanId, Long conversationId, String assistantMessage) {
        saveMemory(command, context, parentSpanId, conversationId, assistantMessage, List.of());
    }

    private void saveMemory(
            AgentRunCommand command,
            AgentContextDTO context,
            Long parentSpanId,
            Long conversationId,
            String assistantMessage,
            List<AgentToolEventDTO> toolEvents
    ) {
        if (!shouldWritePersistentMemory(context)) {
            return;
        }
        String memoryStrategyMode = memoryStrategyMode(context.profile());
        List<RecordMemoryCommand> preferences = preferenceExtractor.extract(
                command.tenantId(),
                command.userId(),
                command.applicationId(),
                command.profileId(),
                conversationId,
                command.userInput()
        );
        List<RecordMemoryCommand> experienceMemories = experienceMemories(
                command,
                conversationId,
                assistantMessage,
                toolEvents,
                memoryStrategyMode
        );
        TraceSpanDTO span = safeStartSpan(command.traceId(), parentSpanId, "memory.write", "MEMORY",
                objectMapper.createObjectNode()
                        .put("summaryCount", 1)
                        .put("preferenceCount", preferences.size())
                        .put("experienceCount", experienceMemories.size()));
        try {
            memoryWriteService.record(new RecordMemoryCommand(
                    command.tenantId(),
                    command.userId(),
                    command.applicationId(),
                    command.profileId(),
                    MEMORY_TYPE_SUMMARY,
                    "User: " + command.userInput() + "\nAssistant: " + assistantMessage,
                    conversationId,
                    null,
                    List.of(),
                    null,
                    null,
                    memoryStrategyMode
            ));
            for (RecordMemoryCommand preference : preferences) {
                memoryWriteService.record(withMemoryStrategy(preference, memoryStrategyMode));
            }
            for (RecordMemoryCommand experienceMemory : experienceMemories) {
                memoryWriteService.record(experienceMemory);
            }
            safeFinishSpan(span, "SUCCESS", null, null);
        } catch (Exception ex) {
            safeFinishSpan(span, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    private List<RecordMemoryCommand> experienceMemories(
            AgentRunCommand command,
            Long conversationId,
            String assistantMessage,
            List<AgentToolEventDTO> toolEvents,
            String memoryStrategyMode
    ) {
        if (toolEvents == null || toolEvents.isEmpty()) {
            return List.of();
        }
        List<AgentToolEventDTO> observations = toolEvents.stream()
                .filter(event -> event != null && !"action".equals(event.type()))
                .toList();
        if (observations.isEmpty()) {
            return List.of();
        }
        List<RecordMemoryCommand> memories = new ArrayList<>();
        for (AgentToolEventDTO event : observations) {
            if (isToolFailureOutput(event.content())) {
                memories.add(new RecordMemoryCommand(
                        command.tenantId(),
                        command.userId(),
                        command.applicationId(),
                        command.profileId(),
                        "TOOL_FAILURE",
                        toolFailureMemoryContent(command, event),
                        conversationId,
                        "tool_failure",
                        List.of("tool_failure", "tool:" + event.toolName()),
                        0.7,
                        "EXPERIENCE_SKILL",
                        memoryStrategyMode
                ));
            }
        }
        memories.add(new RecordMemoryCommand(
                command.tenantId(),
                command.userId(),
                command.applicationId(),
                command.profileId(),
                "REFLECTION",
                reflectionMemoryContent(command, assistantMessage, observations),
                conversationId,
                "reflection",
                reflectionTags(observations),
                0.55,
                "EXPERIENCE_SKILL",
                memoryStrategyMode
        ));
        return memories;
    }

    private String toolFailureMemoryContent(AgentRunCommand command, AgentToolEventDTO event) {
        return "Tool execution failed. "
                + "Tool: " + event.toolName() + ". "
                + "User request: " + summarizeText(command.userInput()) + ". "
                + "Failure: " + summarizeText(event.content());
    }

    private String reflectionMemoryContent(
            AgentRunCommand command,
            String assistantMessage,
            List<AgentToolEventDTO> observations
    ) {
        boolean failed = observations.stream().anyMatch(event -> isToolFailureOutput(event.content()));
        String tools = observations.stream()
                .map(AgentToolEventDTO::toolName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .limit(5)
                .toList()
                .toString();
        return (failed ? "Tool execution completed with failures. " : "Tool execution succeeded. ")
                + "Tools: " + tools + ". "
                + "User request: " + summarizeText(command.userInput()) + ". "
                + "Final answer: " + summarizeText(assistantMessage);
    }

    private List<String> reflectionTags(List<AgentToolEventDTO> observations) {
        List<String> tags = new ArrayList<>();
        tags.add("reflection");
        tags.add(observations.stream().anyMatch(event -> isToolFailureOutput(event.content()))
                ? "tool_failure"
                : "tool_success");
        observations.stream()
                .map(AgentToolEventDTO::toolName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .limit(3)
                .map(name -> "tool:" + name)
                .forEach(tags::add);
        return tags;
    }

    private boolean shouldWritePersistentMemory(AgentContextDTO context) {
        String mode = memoryStrategyMode(context.profile());
        return "READ_WRITE".equals(mode);
    }

    private RecordMemoryCommand withMemoryStrategy(RecordMemoryCommand command, String memoryStrategyMode) {
        return new RecordMemoryCommand(
                command.tenantId(),
                command.userId(),
                command.applicationId(),
                command.profileId(),
                command.memoryType(),
                command.content(),
                command.sourceConversationId(),
                command.memoryCategory(),
                command.tags(),
                command.importance(),
                command.slotHint(),
                memoryStrategyMode
        );
    }

    private String memoryStrategyMode(ProfileDTO profile) {
        if (profile == null || profile.memoryStrategy() == null || !profile.memoryStrategy().hasNonNull("mode")) {
            return "READ_WRITE";
        }
        String mode = profile.memoryStrategy().get("mode").asText("READ_WRITE");
        return mode == null || mode.isBlank() ? "READ_WRITE" : mode.strip().toUpperCase(Locale.ROOT);
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
        safeFinishSpan(span, status, errorCode, errorMessage, span == null ? null : span.attributes());
    }

    private void safeFinishSpan(TraceSpanDTO span, String status, String errorCode, String errorMessage, JsonNode attributes) {
        if (span == null || span.id() == null) {
            return;
        }
        try {
            traceService.finishSpan(new FinishTraceSpanCommand(span.id(), status, errorCode, errorMessage, attributes));
        } catch (Exception ex) {
            // Trace is diagnostic data; it must not break the agent run.
        }
    }

    private void notifyPreModelCall(ModelHookContext context) {
        for (AgentRuntimeHook hook : runtimeHooks) {
            try {
                hook.preModelCall(context);
            } catch (Exception ex) {
                // Hooks are observational extension points; they must not control the runtime.
            }
        }
    }

    private void notifyPostModelCall(ModelHookContext context, ModelInvokeResult result, Exception error) {
        for (AgentRuntimeHook hook : runtimeHooks) {
            try {
                hook.postModelCall(context, result, error);
            } catch (Exception ex) {
                // Hooks are observational extension points; they must not control the runtime.
            }
        }
    }

    private void notifyPreToolCall(ToolHookContext context) {
        for (AgentRuntimeHook hook : runtimeHooks) {
            try {
                hook.preToolCall(context);
            } catch (Exception ex) {
                // Hooks are observational extension points; they must not control the runtime.
            }
        }
    }

    private void notifyPostToolCall(ToolHookContext context, String output, Exception error) {
        for (AgentRuntimeHook hook : runtimeHooks) {
            try {
                hook.postToolCall(context, output, error);
            } catch (Exception ex) {
                // Hooks are observational extension points; they must not control the runtime.
            }
        }
    }

    private void notifyPostFinalAnswer(FinalAnswerHookContext context) {
        for (AgentRuntimeHook hook : runtimeHooks) {
            try {
                hook.postFinalAnswer(context);
            } catch (Exception ex) {
                // Hooks are observational extension points; they must not control the runtime.
            }
        }
    }

    private void recordModelSpanResult(ObjectNode attributes, ModelInvokeResult result) {
        if (attributes == null || result == null) {
            return;
        }
        attributes.put("providerId", result.providerId() == null ? 0L : result.providerId());
        attributes.put("providerType", result.providerType() == null ? "" : result.providerType());
        attributes.put("modelName", result.modelName() == null ? "" : result.modelName());
        attributes.put("assistantChars", result.assistantMessage() == null ? 0 : result.assistantMessage().length());
        attributes.put("toolCallCount", result.toolCalls() == null ? 0 : result.toolCalls().size());
        if (result.usage() != null) {
            attributes.put("promptTokens", result.usage().promptTokens());
            attributes.put("completionTokens", result.usage().completionTokens());
            attributes.put("totalTokens", result.usage().totalTokens());
            attributes.put("estimatedTokens", result.usage().estimated());
        }
    }

    private void recordToolSpanResult(ObjectNode attributes, String output) {
        if (attributes == null) {
            return;
        }
        attributes.put("success", !isToolFailureOutput(output));
        attributes.put("resultSummary", summarizeText(output));
        attributes.put("resultChars", output == null ? 0 : output.length());
    }

    private ToolHookContext toolHookContext(
            AgentRunCommand command,
            ToolCall toolCall,
            AgentToolValidationResult validation,
            int step,
            ObjectNode attributes
    ) {
        AgentToolRiskLevel riskLevel = validation == null || validation.tool() == null
                ? AgentToolRiskLevel.LOW
                : validation.tool().riskLevel();
        boolean readOnly = validation != null && validation.tool() != null && validation.tool().readOnly();
        List<String> resourceKeys = validation == null || validation.tool() == null
                ? List.of()
                : validation.tool().resourceKeys();
        return new ToolHookContext(
                command.traceId(),
                command.tenantId(),
                command.applicationId(),
                command.userId(),
                command.profileId(),
                step,
                toolCall.sourceType(),
                toolCall.name(),
                riskLevel,
                readOnly,
                resourceKeys,
                attributes == null ? "" : attributes.path("argumentsSummary").asText("")
        );
    }

    private boolean isToolFailureOutput(String output) {
        return output != null && (output.startsWith("[tool failed]")
                || output.startsWith("[tool confirm required]")
                || output.startsWith("[tool call rejected]"));
    }

    private String summarizeJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return summarizeText(node.toString());
    }

    private String summarizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").strip();
        int maxChars = 240;
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
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

    private void recordContextBudgetSnapshot(AgentRunCommand command, Long parentSpanId, AgentContextDTO context) {
        ContextBudgetSnapshotDTO snapshot = context.contextBudgetSnapshot();
        if (snapshot.maxContextTokens() <= 0 && command.maxContextTokens() != null && command.maxContextTokens() > 0) {
            snapshot = snapshot.withMaxContextTokens(command.maxContextTokens());
        }
        TraceSpanDTO span = safeStartSpan(command.traceId(), parentSpanId, "context.budget.snapshot", "CONTEXT",
                objectMapper.createObjectNode()
                        .set("contextBudgetSnapshot", contextBudgetSnapshotNode(snapshot)));
        safeFinishSpan(span, "SUCCESS", null, null);
    }

    private String buildFinalAnswer(AgentRunCommand command, Long parentSpanId, String rawAssistantMessage) {
        ObjectNode attributes = objectMapper.createObjectNode()
                .put("rawChars", rawAssistantMessage == null ? 0 : rawAssistantMessage.length());
        TraceSpanDTO span = null;
        try {
            String finalAnswer = finalResponseSynthesizer.cleanUserVisibleAnswer(rawAssistantMessage);
            attributes.put("finalChars", finalAnswer == null ? 0 : finalAnswer.length());
            attributes.put("changed", !java.util.Objects.equals(rawAssistantMessage, finalAnswer));
            span = safeStartSpan(command.traceId(), parentSpanId, "final.answer.build", "AGENT", attributes);
            notifyPostFinalAnswer(new FinalAnswerHookContext(
                    command.traceId(),
                    command.tenantId(),
                    command.applicationId(),
                    command.userId(),
                    command.profileId(),
                    rawAssistantMessage == null ? 0 : rawAssistantMessage.length(),
                    finalAnswer == null ? 0 : finalAnswer.length(),
                    !java.util.Objects.equals(rawAssistantMessage, finalAnswer)
            ));
            safeFinishSpan(span, "SUCCESS", null, null);
            return finalAnswer;
        } catch (Exception ex) {
            safeFinishSpan(span, "FAILED", errorCode(ex), errorMessage(ex));
            throw ex;
        }
    }

    private String compactMessage(AgentRunCommand command, Long parentSpanId, String role, String content) {
        MicroCompactResult result = microCompactService.compact(role, content);
        if (!result.compacted()) {
            return result.content();
        }
        TraceSpanDTO span = safeStartSpan(command.traceId(), parentSpanId, "compact.micro", "CONTEXT",
                objectMapper.createObjectNode()
                        .put("role", role)
                        .put("originalChars", result.originalChars())
                        .put("compactedChars", result.compactedChars()));
        safeFinishSpan(span, "SUCCESS", null, null);
        return result.content();
    }

    private List<ModelMessage> compactMessages(AgentRunCommand command, Long parentSpanId, List<ModelMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ModelMessage> compacted = new ArrayList<>(messages.size());
        for (ModelMessage message : messages) {
            String content = compactMessage(command, parentSpanId, message.role(), message.content());
            compacted.add(new ModelMessage(message.role(), content));
        }
        return compacted;
    }

    private ObjectNode contextBudgetSnapshotNode(ContextBudgetSnapshotDTO snapshot) {
        return objectMapper.createObjectNode()
                .put("maxContextTokens", snapshot.maxContextTokens())
                .put("systemTokens", snapshot.systemTokens())
                .put("profileTokens", snapshot.profileTokens())
                .put("historyTokens", snapshot.historyTokens())
                .put("memoryTokens", snapshot.memoryTokens())
                .put("toolsTokens", snapshot.toolsTokens())
                .put("experienceTokens", snapshot.experienceTokens())
                .put("ragTokens", snapshot.ragTokens())
                .put("currentInputTokens", snapshot.currentInputTokens())
                .put("apiMessagesTokens", snapshot.apiMessagesTokens())
                .put("remainingTokens", snapshot.remainingTokens())
                .put("truncated", snapshot.truncated());
    }

    private Long spanId(TraceSpanDTO span) {
        return span == null ? null : span.id();
    }

    private String errorCode(Exception ex) {
        return ex instanceof BizException bizException ? bizException.getCode() : ErrorCode.INTERNAL_ERROR.getCode();
    }

    private String errorMessage(Exception ex) {
        return ex.getMessage() == null ? "Agent runtime failed" : ex.getMessage();
    }

    private record ToolCall(AgentToolSourceType sourceType, String name, JsonNode arguments) {
    }

    private record ToolExecutionResult(ToolCall toolCall, String output) {
    }

    private static final class BufferedStreamCallback implements ModelStreamCallback {

        private final List<String> tokens = new ArrayList<>();

        @Override
        public void onToken(String token) {
            if (token != null && !token.isEmpty()) {
                tokens.add(token);
            }
        }

        private List<String> tokens() {
            return tokens;
        }
    }

    private record ToolRequestBatch(List<ToolCall> toolCalls) {

        private ToolRequestBatch {
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }

        private static ToolRequestBatch empty() {
            return new ToolRequestBatch(List.of());
        }

        private boolean isEmpty() {
            return toolCalls.isEmpty();
        }
    }
}
