package com.ls.agent.gateway.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.api.AgentRuntimeService;
import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.dto.AgentRunResult;
import com.ls.agent.core.identity.api.ApiKeyService;
import com.ls.agent.core.identity.dto.ApiKeyAuthResult;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.FinishTraceRootCommand;
import com.ls.agent.core.trace.command.StartTraceRootCommand;
import com.ls.agent.gateway.application.DirectModelRunService;
import com.ls.agent.gateway.dto.GatewayChatRequest;
import com.ls.agent.gateway.dto.SseEventPayload;
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

@RestController
public class InternalAiController {

    private static final String MODE_AGENT = "agent";
    private static final String MODE_NONE = "none";

    private final AgentRuntimeService agentRuntimeService;
    private final DirectModelRunService directModelRunService;
    private final ApiKeyService apiKeyService;
    private final TraceService traceService;
    private final QuotaFilter quotaFilter;
    private final SensitiveDataFilter sensitiveDataFilter;
    private final AlertFilter alertFilter;
    private final ObjectMapper objectMapper;
    private final String internalToken;

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

    @GetMapping(value = "/internal/ai/stream-test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamTest(@RequestHeader HttpHeaders headers) {
        verifyInternalToken(headers);
        String traceId = newTraceId();
        return sse(output -> {
            writeEvent(output, "thinking", payload("thinking", traceId, null, 1, "start", Map.of()));
            writeEvent(output, "action", payload("action", traceId, null, 2, "call calculator", Map.of("toolName", "calculator")));
            writeEvent(output, "observation", payload("observation", traceId, null, 3, "calculator returned 4667", Map.of("success", true)));
            writeEvent(output, "message", payload("message", traceId, null, 4, "result is 4667", Map.of()));
            writeEvent(output, "done", payload("done", traceId, null, 5, null, Map.of()));
        });
    }

    @PostMapping(value = "/internal/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> internalChatStream(
            @RequestHeader HttpHeaders headers,
            @RequestBody GatewayChatRequest request
    ) {
        verifyInternalToken(headers);
        return runChatStream(request, request.tenantId(), request.applicationId(), request.userId(), "INTERNAL_WEB", null);
    }

    @PostMapping(value = "/api/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> apiChatStream(
            @RequestHeader HttpHeaders headers,
            @RequestBody GatewayChatRequest request
    ) {
        String apiKey = bearerToken(headers.getFirst(HttpHeaders.AUTHORIZATION));
        ApiKeyAuthResult auth = apiKeyService.authenticate(apiKey);
        return runChatStream(request, auth.tenantId(), auth.applicationId(), auth.userId(), "API_KEY", apiKeyPrefix(apiKey));
    }

    private ResponseEntity<StreamingResponseBody> runChatStream(
            GatewayChatRequest request,
            Long tenantId,
            Long applicationId,
            Long userId,
            String entrypoint,
            String apiKeyPrefix
    ) {
        String traceId = newTraceId();
        try {
            sensitiveDataFilter.scanRequest(traceId, tenantId, applicationId, userId, request);
            quotaFilter.reserve(traceId, tenantId, applicationId, userId);
        } catch (Exception ex) {
            alertFilter.recordFailure(traceId, tenantId, applicationId, ex);
            throw ex;
        }
        return sse(output -> {
            startTraceRoot(traceId, request, tenantId, applicationId, userId, entrypoint, apiKeyPrefix);
            Long conversationId = null;
            writeEvent(output, "thinking", payload("thinking", traceId, null, 1, "request accepted", Map.of()));
            try {
                if (MODE_NONE.equalsIgnoreCase(request.agentMode())) {
                    ModelInvokeResult result = directModelRunService.run(
                            traceId,
                            tenantId,
                            applicationId,
                            userId,
                            request.profileId(),
                            request.modelConfigId(),
                            request.messages()
                    );
                    writeEvent(output, "message", payload("message", traceId, null, 2, result.assistantMessage(), Map.of()));
                    writeEvent(output, "done", payload("done", traceId, null, 3, null, Map.of("modelConfigId", result.modelConfigId())));
                    quotaFilter.commit(traceId, totalTokens(result.usage()));
                    finishTraceRoot(traceId, null, "SUCCESS", null, null);
                    return;
                }
                if (request.agentMode() != null && !MODE_AGENT.equalsIgnoreCase(request.agentMode())) {
                    throw new BizException(ErrorCode.REQUEST_INVALID, "agentMode is invalid");
                }
                AgentRunResult result = agentRuntimeService.run(new AgentRunCommand(
                        tenantId,
                        userId,
                        applicationId,
                        request.profileId(),
                        request.conversationId(),
                        request.message(),
                        traceId,
                        request.enabledSkillIds(),
                        request.enabledMcpToolIds(),
                        null
                ));
                conversationId = result.conversationId();
                writeEvent(output, "message", payload("message", traceId, result.conversationId(), 2, result.assistantMessage(), Map.of()));
                writeEvent(output, "done", payload("done", traceId, result.conversationId(), 3, null, Map.of()));
                quotaFilter.commit(traceId, totalTokens(result.usage()));
                finishTraceRoot(traceId, conversationId, "SUCCESS", null, null);
            } catch (Exception ex) {
                quotaFilter.release(traceId);
                finishTraceRoot(traceId, conversationId, "FAILED", errorCode(ex), errorMessage(ex));
                alertFilter.recordFailure(traceId, tenantId, applicationId, ex);
                throw ex;
            }
        });
    }

    private ResponseEntity<StreamingResponseBody> sse(StreamingResponseBody body) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(output -> {
                    try {
                        body.writeTo(output);
                    } catch (Exception ex) {
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

    private void writeEvent(OutputStream output, String event, SseEventPayload payload) throws IOException {
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
}
