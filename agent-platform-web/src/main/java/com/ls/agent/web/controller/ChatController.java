package com.ls.agent.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.web.client.GatewayClient;
import com.ls.agent.web.client.InternalChatStreamRequest;
import com.ls.agent.web.dto.ChatStreamRequest;
import com.ls.agent.web.security.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class ChatController {

    private final GatewayClient gatewayClient;
    private final ObjectMapper objectMapper;

    public ChatController(GatewayClient gatewayClient, ObjectMapper objectMapper) {
        this.gatewayClient = gatewayClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/api/chat/stream-test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamTest(CurrentUser currentUser) {
        return sse(gatewayClient::streamTest);
    }

    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chatStream(
            CurrentUser currentUser,
            @RequestBody ChatStreamRequest request
    ) {
        InternalChatStreamRequest internalRequest = new InternalChatStreamRequest(
                currentUser.tenantId(),
                request.applicationId(),
                currentUser.userId(),
                "WEB",
                request.agentMode(),
                request.profileId(),
                request.conversationId(),
                request.message(),
                request.enabledSkillIds(),
                request.enabledMcpToolIds(),
                request.clientRequestId(),
                request.modelConfigId(),
                request.messages(),
                request.stream()
        );
        return sse(output -> gatewayClient.chatStream(internalRequest, output));
    }

    private ResponseEntity<StreamingResponseBody> sse(StreamingResponseBody body) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(output -> {
                    try {
                        body.writeTo(output);
                    } catch (Exception ex) {
                        writeErrorEvent(output, ex);
                    }
                });
    }

    private void writeErrorEvent(OutputStream output, Exception ex) throws IOException {
        String message = ex.getMessage() == null ? "SSE stream failed" : ex.getMessage();
        Map<String, String> payload = Map.of(
                "type", "error",
                "content", message
        );
        output.write("event: error\n".getBytes(StandardCharsets.UTF_8));
        output.write(("data: " + objectMapper.writeValueAsString(payload) + "\n\n")
                .getBytes(StandardCharsets.UTF_8));
        output.flush();
    }
}
