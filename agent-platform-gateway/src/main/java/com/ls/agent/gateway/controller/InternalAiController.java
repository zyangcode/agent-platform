package com.ls.agent.gateway.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.api.AgentRuntimeService;
import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.dto.AgentRunResult;
import com.ls.agent.core.agent.dto.AgentToolEventDTO;
import com.ls.agent.core.identity.api.ApiKeyService;
import com.ls.agent.core.identity.dto.ApiKeyAuthResult;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.team.dto.TeamRuntimeEventDTO;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.FinishTraceRootCommand;
import com.ls.agent.core.trace.command.StartTraceRootCommand;
import com.ls.agent.gateway.application.DirectModelRunService;
import com.ls.agent.gateway.dto.GatewayChatRequest;
import com.ls.agent.gateway.dto.SseEventPayload;
import com.ls.agent.gateway.dto.TeamSseEventPayload;
import com.ls.agent.gateway.filter.AlertFilter;
import com.ls.agent.gateway.filter.QuotaFilter;
import com.ls.agent.gateway.filter.SensitiveDataFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * 内部与外部 AI 流量网关控制器。
 * 负责处理来自内部管理端和外部 API Key 的流式对话请求，集成限流、敏感词过滤、链路追踪和告警功能。
 */
@RestController
public class InternalAiController {

    /** 运行模式：智能体模式 */
    private static final String MODE_AGENT = "agent";
    /** 运行模式：直接调用模型模式（无 Agent） */
    private static final String MODE_NONE = "none";

    /** Agent 运行时服务 */
    private final AgentRuntimeService agentRuntimeService;
    /** 直接模型调用服务 */
    private final DirectModelRunService directModelRunService;
    /** API Key 认证服务 */
    private final ApiKeyService apiKeyService;
    /** 链路追踪服务 */
    private final TraceService traceService;
    /** 配额/限流过滤器 */
    private final QuotaFilter quotaFilter;
    /** 敏感数据/内容安全过滤器 */
    private final SensitiveDataFilter sensitiveDataFilter;
    /** 异常告警过滤器 */
    private final AlertFilter alertFilter;
    /** JSON 解析器 */
    private final ObjectMapper objectMapper;
    /** 内部调用鉴权 Token */
    private final String internalToken;

    /**
     * 构造函数注入所有依赖组件。
     */
    public InternalAiController(
            AgentRuntimeService agentRuntimeService,
            DirectModelRunService directModelRunService,
            ApiKeyService apiKeyService,
            TraceService traceService,
            QuotaFilter quotaFilter,
            SensitiveDataFilter sensitiveDataFilter,
            AlertFilter alertFilter,
            ObjectMapper objectMapper,
            @Value("${gateway.internal-token:dev-internal-token}") String internalToken
    ) {
        this.agentRuntimeService = agentRuntimeService;
        this.directModelRunService = directModelRunService;
        this.apiKeyService = apiKeyService;
        this.traceService = traceService;
        this.quotaFilter = quotaFilter;
        this.sensitiveDataFilter = sensitiveDataFilter;
        this.alertFilter = alertFilter;
        this.objectMapper = objectMapper;
        this.internalToken = internalToken;
    }

    /**
     * 内部流式测试接口。
     * 用于验证 SSE（Server-Sent Events）推送机制是否正常工作。
     *
     * @param headers 请求头
     * @return 模拟的 SSE 事件流
     */
    @GetMapping(value = "/internal/ai/stream-test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamTest(@RequestHeader HttpHeaders headers) {
        // 1. 验证内部安全 Token
        verifyInternalToken(headers);
        // 2. 生成新的追踪 ID
        String traceId = newTraceId();
        // 3. 返回 SSE 响应体，模拟 Agent 思考过程
        return sse(output -> {
            writeEvent(output, "thinking", payload("thinking", traceId, null, 1, "start", Map.of()));
            writeEvent(output, "action", payload("action", traceId, null, 2, "call calculator", Map.of("toolName", "calculator")));
            writeEvent(output, "observation", payload("observation", traceId, null, 3, "calculator returned 4667", Map.of("success", true)));
            writeEvent(output, "message", payload("message", traceId, null, 4, "result is 4667", Map.of()));
            writeEvent(output, "done", payload("done", traceId, null, 5, null, Map.of()));
        });
    }

    /**
     * 内部管理端流式对话接口。
     * 使用 internal-token 进行鉴权。
     */
    @PostMapping(value = "/internal/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> internalChatStream(
            @RequestHeader HttpHeaders headers,
            @RequestBody GatewayChatRequest request
    ) {
        // 1. 验证内部 Token
        verifyInternalToken(headers);
        // 2. 执行统一的对话流逻辑
        return runChatStream(request, request.tenantId(), request.applicationId(), request.userId(), "INTERNAL_WEB", null);
    }

    /**
     * 外部 API 流式对话接口。
     * 使用 API Key 进行鉴权。
     */
    @PostMapping(value = "/api/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> apiChatStream(
            @RequestHeader HttpHeaders headers,
            @RequestBody GatewayChatRequest request
    ) {
        // 1. 从 Authorization 头提取 API Key
        String apiKey = bearerToken(headers.getFirst(HttpHeaders.AUTHORIZATION));
        // 2. 认证并获取关联的身份信息
        ApiKeyAuthResult auth = apiKeyService.authenticate(apiKey);
        // 3. 执行对话流逻辑
        return runChatStream(request, auth.tenantId(), auth.applicationId(), auth.userId(), "API_KEY", apiKeyPrefix(apiKey));
    }

    /**
     * 统一执行 AI 对话流的核心方法。
     * 包含完整的过滤器链：安全扫描、配额预扣、链路追踪、异常处理等。
     */
    private ResponseEntity<StreamingResponseBody> runChatStream(
            GatewayChatRequest request,
            Long tenantId,
            Long applicationId,
            Long userId,
            String entrypoint,
            String apiKeyPrefix
    ) {
        // 1. 生成唯一的 TraceId 贯穿整个请求周期
        String traceId = newTraceId();
        try {
            // 2. 敏感词/安全扫描
            sensitiveDataFilter.scanRequest(traceId, tenantId, applicationId, userId, request);
            // 3. 配额预扣（如 Token 限流）
            quotaFilter.reserve(traceId, tenantId, applicationId, userId);
        } catch (Exception ex) {
            // 失败记录告警并抛出
            alertFilter.recordFailure(traceId, tenantId, applicationId, ex);
            throw ex;
        }

        // 4. 返回流式响应体
        return sse(output -> {
            // A. 开始链路追踪记录
            startTraceRoot(traceId, request, tenantId, applicationId, userId, entrypoint, apiKeyPrefix);
            Long conversationId = null;
            // B. 推送“已接收请求”的思考事件
            writeEvent(output, "thinking", payload("thinking", traceId, null, 1, "request accepted", Map.of()));

            try {
                // 情况一：直接调用模型模式（不启动 Agent）
                if (MODE_NONE.equalsIgnoreCase(request.agentMode())) {
                    ModelInvokeResult result = directModelRunService.run(
                            traceId, tenantId, applicationId, userId,
                            request.profileId(), request.modelConfigId(), request.messages()
                    );
                    // 推送结果消息并结束
                    writeEvent(output, "message", payload("message", traceId, null, 2, result.assistantMessage(), Map.of()));
                    writeEvent(output, "done", payload("done", traceId, null, 3, null, Map.of("modelConfigId", result.modelConfigId())));
                    // 提交实际 Token 消耗
                    quotaFilter.commit(traceId, totalTokens(result.usage()));
                    finishTraceRoot(traceId, null, "SUCCESS", null, null);
                    return;
                }

                // 情况二：智能体（Agent）模式
                if (request.agentMode() != null && !MODE_AGENT.equalsIgnoreCase(request.agentMode())) {
                    throw new BizException(ErrorCode.REQUEST_INVALID, "agentMode is invalid");
                }

                // 调用 Agent 运行时，通过回调函数实时推送事件
                AgentRunResult result = agentRuntimeService.run(new AgentRunCommand(
                        tenantId, userId, applicationId, request.profileId(),
                        request.conversationId(), request.message(), traceId,
                        request.enabledSkillIds(), request.enabledMcpToolIds(), null
                ), event -> writeTeamEvent(output, event));

                conversationId = result.conversationId();
                int step = 2;
                // 推送 Agent 调用工具的中间过程
                for (AgentToolEventDTO event : result.toolEvents()) {
                    writeEvent(output, event.type(), payload(
                            event.type(), traceId, result.conversationId(), step++,
                            event.content(), toolEventMetadata(event)
                    ));
                }
                // 推送最终回复消息
                writeEvent(output, "message", payload("message", traceId, result.conversationId(), step++, result.assistantMessage(), Map.of()));
                writeEvent(output, "done", payload("done", traceId, result.conversationId(), step, null, Map.of()));

                // 提交配额并结束追踪
                quotaFilter.commit(traceId, totalTokens(result.usage()));
                finishTraceRoot(traceId, conversationId, "SUCCESS", null, null);
            } catch (Exception ex) {
                // 异常处理：回滚配额、记录失败状态、触发告警
                quotaFilter.release(traceId);
                finishTraceRoot(traceId, conversationId, "FAILED", errorCode(ex), errorMessage(ex));
                alertFilter.recordFailure(traceId, tenantId, applicationId, ex);
                throw ex;
            }
        });
    }

    /**
     * 构建标准 SSE 响应包装器。
     */
    private ResponseEntity<StreamingResponseBody> sse(StreamingResponseBody body) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(output -> {
                    try {
                        body.writeTo(output);
                    } catch (Exception ex) {
                        // 捕获流写入异常，尝试推送错误事件给前端
                        writeEvent(output, "error", payload("error", null, null, 0, errorMessage(ex), Map.of()));
                    }
                });
    }

    private void verifyInternalToken(HttpHeaders headers) {
        String token = headers.getFirst("X-Internal-Token");
        if (!internalToken.equals(token)) {
            throw new BizException(ErrorCode.AUTH_UNAUTHORIZED, "Invalid internal token");
        }
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.API_KEY_INVALID, "API key is required");
        }
        return authorization.substring("Bearer ".length()).strip();
    }

    private void startTraceRoot(
            String traceId,
            GatewayChatRequest request,
            Long tenantId,
            Long applicationId,
            Long userId,
            String entrypoint,
            String apiKeyPrefix
    ) {
        try {
            traceService.startRoot(new StartTraceRootCommand(
                    traceId,
                    tenantId,
                    applicationId,
                    userId,
                    request.profileId(),
                    request.conversationId(),
                    request.clientRequestId(),
                    entrypoint,
                    effectiveAgentMode(request),
                    objectMapper.createObjectNode()
                            .put("channel", request.channel() == null ? "" : request.channel())
                            .put("stream", request.stream() == null || request.stream())
                            .put("apiKeyPrefix", apiKeyPrefix == null ? "" : apiKeyPrefix)
            ));
        } catch (Exception ex) {
            // Trace is diagnostic data; it must not break the chat stream.
        }
    }

    private void finishTraceRoot(
            String traceId,
            Long conversationId,
            String status,
            String errorCode,
            String errorMessage
    ) {
        try {
            traceService.finishRoot(new FinishTraceRootCommand(
                    traceId,
                    conversationId,
                    status,
                    errorCode,
                    errorMessage
            ));
        } catch (Exception ex) {
            // Trace is diagnostic data; it must not break the chat stream.
        }
    }

    private String effectiveAgentMode(GatewayChatRequest request) {
        return request.agentMode() == null ? MODE_AGENT : request.agentMode();
    }

    private String apiKeyPrefix(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        return apiKey.length() <= 8 ? apiKey : apiKey.substring(0, 8);
    }

    private String errorCode(Exception ex) {
        return ex instanceof BizException bizException ? bizException.getCode() : ErrorCode.INTERNAL_ERROR.getCode();
    }

    private String newTraceId() {
        return "tr_" + UUID.randomUUID().toString().replace("-", "");
    }

    private Long totalTokens(com.ls.agent.core.model.dto.ModelUsageDTO usage) {
        return usage == null ? 0L : (long) usage.totalTokens();
    }

    private SseEventPayload payload(String type, String traceId, Long conversationId, int step, String content, Map<String, Object> metadata) {
        return new SseEventPayload(type, traceId, conversationId, step, content, metadata);
    }

    private Map<String, Object> toolEventMetadata(AgentToolEventDTO event) {
        return Map.of(
                "toolType", event.toolType() == null ? "" : event.toolType(),
                "toolName", event.toolName() == null ? "" : event.toolName()
        );
    }

    private void writeTeamEvent(OutputStream output, TeamRuntimeEventDTO event) {
        try {
            writeEvent(output, event.type(), teamPayload(
                    event.type(),
                    event.traceId(),
                    null,
                    event.step() == null ? 0 : event.step(),
                    roleFor(event.type()),
                    event.taskId(),
                    event.toolName(),
                    event.status(),
                    event.message(),
                    event.payload()
            ));
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Team SSE event write failed");
        }
    }

    private TeamSseEventPayload teamPayload(
            String type,
            String traceId,
            Long conversationId,
            int step,
            String role,
            String taskId,
            String toolName,
            String status,
            String content,
            JsonNode payload
    ) {
        return new TeamSseEventPayload(
                type,
                traceId,
                conversationId,
                step,
                role,
                taskId,
                toolName,
                status,
                content,
                payload
        );
    }

    private String roleFor(String eventType) {
        if (TeamRuntimeEventDTO.TYPE_TEAM_PLAN.equals(eventType)) {
            return "PLANNER";
        }
        if (TeamRuntimeEventDTO.TYPE_TEAM_REVIEW.equals(eventType)) {
            return "REVIEWER";
        }
        if (TeamRuntimeEventDTO.TYPE_TEAM_START.equals(eventType)
                || TeamRuntimeEventDTO.TYPE_TEAM_RETRY.equals(eventType)
                || TeamRuntimeEventDTO.TYPE_TEAM_FINAL.equals(eventType)) {
            return "ORCHESTRATOR";
        }
        return "EXECUTOR";
    }

    private void writeEvent(OutputStream output, String event, SseEventPayload payload) throws IOException {
        output.write(("event: " + event + "\n").getBytes(StandardCharsets.UTF_8));
        output.write(("data: " + toJson(payload) + "\n\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private void writeEvent(OutputStream output, String event, TeamSseEventPayload payload) throws IOException {
        output.write(("event: " + event + "\n").getBytes(StandardCharsets.UTF_8));
        output.write(("data: " + toJson(payload) + "\n\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private String errorMessage(Exception ex) {
        return ex.getMessage() == null ? "SSE stream failed" : ex.getMessage();
    }

    private String toJson(SseEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "SSE payload serialization failed");
        }
    }

    private String toJson(TeamSseEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "SSE payload serialization failed");
        }
    }
}
