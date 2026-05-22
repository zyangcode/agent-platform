package com.ls.agent.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.agent.api.AgentRuntimeService;
import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.dto.AgentRunResult;
import com.ls.agent.core.identity.api.ApiKeyService;
import com.ls.agent.core.identity.dto.ApiKeyAuthResult;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.gateway.dto.GatewayChatRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = InternalAiController.class,
        properties = "gateway.internal-token=dev-internal-token"
)
class InternalAiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgentRuntimeService agentRuntimeService;

    @MockBean
    private ModelInvokeService modelInvokeService;

    @MockBean
    private ApiKeyService apiKeyService;

    @Test
    void streamTestRequiresInternalToken() throws Exception {
        mockMvc.perform(get("/internal/ai/stream-test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void streamTestReturnsFixedSseEvents() throws Exception {
        var result = mockMvc.perform(get("/internal/ai/stream-test")
                        .header("X-Internal-Token", "dev-internal-token")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: thinking")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: done")));
    }

    @Test
    void internalChatStreamCallsAgentRuntime() throws Exception {
        when(agentRuntimeService.run(any(AgentRunCommand.class))).thenReturn(agentResult());

        var result = mockMvc.perform(post("/internal/ai/chat/stream")
                        .header("X-Internal-Token", "dev-internal-token")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(internalRequest())))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: message")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("done")));

        ArgumentCaptor<AgentRunCommand> captor = ArgumentCaptor.forClass(AgentRunCommand.class);
        verify(agentRuntimeService).run(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().applicationId()).isEqualTo(20001L);
        assertThat(captor.getValue().userId()).isEqualTo(10001L);
        assertThat(captor.getValue().profileId()).isEqualTo(50001L);
        assertThat(captor.getValue().selectedSkillIds()).containsExactly(60001L);
    }

    @Test
    void internalChatStreamDefaultsToAgentMode() throws Exception {
        when(agentRuntimeService.run(any(AgentRunCommand.class))).thenReturn(agentResult());

        GatewayChatRequest request = new GatewayChatRequest(
                1L,
                20001L,
                10001L,
                "WEB",
                null,
                50001L,
                null,
                "hello",
                null,
                null,
                "client-1",
                null,
                null,
                null
        );

        var result = mockMvc.perform(post("/internal/ai/chat/stream")
                        .header("X-Internal-Token", "dev-internal-token")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: message")));

        verify(agentRuntimeService).run(any(AgentRunCommand.class));
    }

    @Test
    void apiChatStreamAuthenticatesApiKeyAndCallsAgentRuntime() throws Exception {
        when(apiKeyService.authenticate("sk-valid")).thenReturn(new ApiKeyAuthResult(1L, 20001L, 10001L));
        when(agentRuntimeService.run(any(AgentRunCommand.class))).thenReturn(agentResult());

        var result = mockMvc.perform(post("/api/ai/chat/stream")
                        .header("Authorization", "Bearer sk-valid")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiRequest())))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: message")));

        verify(apiKeyService).authenticate("sk-valid");
        verify(agentRuntimeService).run(any(AgentRunCommand.class));
    }

    private GatewayChatRequest internalRequest() {
        return new GatewayChatRequest(
                1L,
                20001L,
                10001L,
                "WEB",
                "agent",
                50001L,
                90001L,
                "hello",
                List.of(60001L),
                List.of(70001L),
                "client-1",
                null,
                null,
                null
        );
    }

    private GatewayChatRequest apiRequest() {
        return new GatewayChatRequest(
                null,
                null,
                null,
                "API",
                "agent",
                50001L,
                null,
                "hello",
                List.of(60001L),
                List.of(70001L),
                "client-1",
                null,
                null,
                null
        );
    }

    private AgentRunResult agentResult() {
        return new AgentRunResult(
                90001L,
                "assistant says hello",
                new ModelUsageDTO(1, 2, 3, true)
        );
    }
}
