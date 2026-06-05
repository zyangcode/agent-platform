package com.ls.agent.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.web.client.GatewayClient;
import com.ls.agent.web.client.InternalChatStreamRequest;
import com.ls.agent.web.dto.ChatStreamRequest;
import com.ls.agent.web.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ChatController.class,
        properties = {
                "security.jwt.secret=test-secret-test-secret-test-secret-test",
                "security.jwt.expires-in-seconds=7200"
        }
)
@Import(WebMvcTestSupport.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private GatewayClient gatewayClient;

    @Test
    void streamTestProxiesGatewaySse() throws Exception {
        doAnswer(invocation -> {
            invocation.getArgument(0, java.io.OutputStream.class)
                    .write("event: thinking\ndata: {\"type\":\"thinking\"}\n\n".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(gatewayClient).streamTest(any());

        var result = mockMvc.perform(get("/api/chat/stream-test")
                        .header("Authorization", bearerToken())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: thinking")));

        verify(gatewayClient).streamTest(any());
    }

    @Test
    void chatStreamAddsCurrentUserAndProxiesGatewaySse() throws Exception {
        doAnswer(invocation -> {
            invocation.getArgument(1, java.io.OutputStream.class)
                    .write("event: message\ndata: {\"content\":\"ok\"}\n\n".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(gatewayClient).chatStream(any(InternalChatStreamRequest.class), any());

        ChatStreamRequest request = new ChatStreamRequest(
                20001L,
                "agent",
                50001L,
                90001L,
                "hello",
                List.of(60001L),
                List.of(70001L),
                "client-1",
                null,
                null,
                true,
                List.of("skill:deploy")
        );

        var result = mockMvc.perform(post("/api/chat/stream")
                        .header("Authorization", bearerToken())
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: message")));

        ArgumentCaptor<InternalChatStreamRequest> captor = ArgumentCaptor.forClass(InternalChatStreamRequest.class);
        verify(gatewayClient).chatStream(captor.capture(), any());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().userId()).isEqualTo(10001L);
        assertThat(captor.getValue().applicationId()).isEqualTo(20001L);
        assertThat(captor.getValue().channel()).isEqualTo("WEB");
        assertThat(captor.getValue().profileId()).isEqualTo(50001L);
        assertThat(captor.getValue().enabledSkillIds()).containsExactly(60001L);
        assertThat(captor.getValue().confirmedToolKeys()).containsExactly("skill:deploy");
    }

    @Test
    void chatStreamPreservesExplicitEmptyToolSelections() throws Exception {
        doAnswer(invocation -> {
            invocation.getArgument(1, java.io.OutputStream.class)
                    .write("event: message\ndata: {\"content\":\"ok\"}\n\n".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(gatewayClient).chatStream(any(InternalChatStreamRequest.class), any());

        String body = """
                {
                  "applicationId": 20001,
                  "agentMode": "agent",
                  "profileId": 50001,
                  "conversationId": null,
                  "message": "hello",
                  "enabledSkillIds": [],
                  "enabledMcpToolIds": [],
                  "clientRequestId": "client-empty-tools",
                  "stream": true,
                  "confirmedToolKeys": []
                }
                """;

        var result = mockMvc.perform(post("/api/chat/stream")
                        .header("Authorization", bearerToken())
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: message")));

        ArgumentCaptor<InternalChatStreamRequest> captor = ArgumentCaptor.forClass(InternalChatStreamRequest.class);
        verify(gatewayClient).chatStream(captor.capture(), any());
        assertThat(captor.getValue().enabledSkillIds()).isEmpty();
        assertThat(captor.getValue().enabledMcpToolIds()).isEmpty();
    }

    @Test
    void chatStreamProxiesTeamRuntimeEventsWithoutMerging() throws Exception {
        doAnswer(invocation -> {
            invocation.getArgument(1, java.io.OutputStream.class)
                    .write(("""
                            event: team_plan
                            data: {"type":"team_plan","role":"PLANNER"}

                            event: message
                            data: {"type":"message","content":"ok"}

                            """).getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(gatewayClient).chatStream(any(InternalChatStreamRequest.class), any());

        ChatStreamRequest request = new ChatStreamRequest(
                20001L,
                "agent",
                50001L,
                null,
                "hello",
                List.of(),
                List.of(),
                "client-1",
                null,
                null,
                true,
                List.of()
        );

        var result = mockMvc.perform(post("/api/chat/stream")
                        .header("Authorization", bearerToken())
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: team_plan")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"team_plan\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"role\":\"PLANNER\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: message")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body.indexOf("event: team_plan")).isLessThan(body.indexOf("event: message"));
        verify(gatewayClient).chatStream(any(InternalChatStreamRequest.class), any());
    }

    @Test
    void streamTestConvertsGatewayFailureToSseErrorEvent() throws Exception {
        doThrow(new BizException(ErrorCode.INTERNAL_ERROR, "Gateway request failed"))
                .when(gatewayClient).streamTest(any());

        var result = mockMvc.perform(get("/api/chat/stream-test")
                        .header("Authorization", bearerToken())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: error")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Gateway request failed")));
    }

    private String bearerToken() {
        CurrentUserDTO user = new CurrentUserDTO(10001L, 1L, "alice", "Alice", List.of("USER"));
        return "Bearer " + jwtTokenService.generate(user);
    }
}
