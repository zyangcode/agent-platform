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
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.gateway.dto.GatewayChatRequest;
import com.ls.agent.gateway.dto.SseEventPayload;
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
    private final ModelInvokeService modelInvokeService;
    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;
    private final String internalToken;

    public InternalAiController(
            AgentRuntimeService agentRuntimeService,
            ModelInvokeService modelInvokeService,
            ApiKeyService apiKeyService,
            ObjectMapper objectMapper,
            @Value("${gateway.internal-token:dev-internal-token}") String internalToken
    ) {
        this.agentRuntimeService = agentRuntimeService;
        this.modelInvokeService = modelInvokeService;
        this.apiKeyService = apiKeyService;
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
        return runChatStream(request, request.tenantId(), request.applicationId(), request.userId());
    }

    @PostMapping(value = "/api/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> apiChatStream(
            @RequestHeader HttpHeaders headers,
            @RequestBody GatewayChatRequest request
    ) {
        String apiKey = bearerToken(headers.getFirst(HttpHeaders.AUTHORIZATION));
        ApiKeyAuthResult auth = apiKeyService.authenticate(apiKey);
        return runChatStream(request, auth.tenantId(), auth.applicationId(), auth.userId());
    }

    private ResponseEntity<StreamingResponseBody> runChatStream(
            GatewayChatRequest request,
            Long tenantId,
            Long applicationId,
            Long userId
    ) {
        String traceId = newTraceId();
        return sse(output -> {
            writeEvent(output, "thinking", payload("thinking", traceId, null, 1, "request accepted", Map.of()));
            if (MODE_NONE.equalsIgnoreCase(request.agentMode())) {
                ModelInvokeResult result = modelInvokeService.invoke(new ModelInvokeCommand(
                        request.modelConfigId(),
                        request.messages(),
                        null,
                        false
                ));
                writeEvent(output, "message", payload("message", traceId, null, 2, result.assistantMessage(), Map.of()));
                writeEvent(output, "done", payload("done", traceId, null, 3, null, Map.of("modelConfigId", result.modelConfigId())));
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
            writeEvent(output, "message", payload("message", traceId, result.conversationId(), 2, result.assistantMessage(), Map.of()));
            writeEvent(output, "done", payload("done", traceId, result.conversationId(), 3, null, Map.of()));
        });
    }

    private ResponseEntity<StreamingResponseBody> sse(StreamingResponseBody body) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
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

    private String newTraceId() {
        return "tr_" + UUID.randomUUID().toString().replace("-", "");
    }

    private SseEventPayload payload(String type, String traceId, Long conversationId, int step, String content, Map<String, Object> metadata) {
        return new SseEventPayload(type, traceId, conversationId, step, content, metadata);
    }

    private void writeEvent(OutputStream output, String event, SseEventPayload payload) throws IOException {
        output.write(("event: " + event + "\n").getBytes(StandardCharsets.UTF_8));
        output.write(("data: " + toJson(payload) + "\n\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private String toJson(SseEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "SSE payload serialization failed");
        }
    }
}
