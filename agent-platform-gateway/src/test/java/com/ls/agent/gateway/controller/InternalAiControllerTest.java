package com.ls.agent.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.api.AgentRuntimeService;
import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.dto.AgentRunResult;
import com.ls.agent.core.identity.api.ApiKeyService;
import com.ls.agent.core.identity.dto.ApiKeyAuthResult;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.quota.api.QuotaService;
import com.ls.agent.core.quota.command.CommitQuotaReservationCommand;
import com.ls.agent.core.quota.command.ReleaseQuotaReservationCommand;
import com.ls.agent.core.quota.command.ReserveQuotaCommand;
import com.ls.agent.core.quota.dto.QuotaReservationDTO;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.FinishTraceRootCommand;
import com.ls.agent.core.trace.command.StartTraceRootCommand;
import com.ls.agent.gateway.dto.GatewayChatRequest;
import com.ls.agent.gateway.filter.QuotaFilter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
@Import(QuotaFilter.class)
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

    @MockBean
    private TraceService traceService;

    @MockBean
    private QuotaService quotaService;

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
        when(quotaService.reserve(any(ReserveQuotaCommand.class))).thenReturn(reservation());
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

        ArgumentCaptor<StartTraceRootCommand> startCaptor = ArgumentCaptor.forClass(StartTraceRootCommand.class);
        verify(traceService).startRoot(startCaptor.capture());
        assertThat(startCaptor.getValue().tenantId()).isEqualTo(1L);
        assertThat(startCaptor.getValue().entrypoint()).isEqualTo("INTERNAL_WEB");
        assertThat(startCaptor.getValue().agentMode()).isEqualTo("agent");

        ArgumentCaptor<FinishTraceRootCommand> finishCaptor = ArgumentCaptor.forClass(FinishTraceRootCommand.class);
        verify(traceService).finishRoot(finishCaptor.capture());
        assertThat(finishCaptor.getValue().traceId()).isEqualTo(startCaptor.getValue().traceId());
        assertThat(finishCaptor.getValue().conversationId()).isEqualTo(90001L);
        assertThat(finishCaptor.getValue().status()).isEqualTo("SUCCESS");

        verify(quotaService).reserve(any(ReserveQuotaCommand.class));
        ArgumentCaptor<CommitQuotaReservationCommand> commitCaptor =
                ArgumentCaptor.forClass(CommitQuotaReservationCommand.class);
        verify(quotaService).commit(commitCaptor.capture());
        assertThat(commitCaptor.getValue().actualTokens()).isEqualTo(3L);
    }

    @Test
    void internalChatStreamDefaultsToAgentMode() throws Exception {
        when(quotaService.reserve(any(ReserveQuotaCommand.class))).thenReturn(reservation());
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
    void internalChatStreamConvertsRuntimeFailureToSseErrorEvent() throws Exception {
        when(quotaService.reserve(any(ReserveQuotaCommand.class))).thenReturn(reservation());
        doThrow(new BizException(ErrorCode.REQUEST_INVALID, "Agent failed"))
                .when(agentRuntimeService).run(any(AgentRunCommand.class));

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
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: error")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Agent failed")));

        ArgumentCaptor<FinishTraceRootCommand> finishCaptor = ArgumentCaptor.forClass(FinishTraceRootCommand.class);
        verify(traceService).finishRoot(finishCaptor.capture());
        assertThat(finishCaptor.getValue().status()).isEqualTo("FAILED");
        assertThat(finishCaptor.getValue().errorMessage()).contains("Agent failed");
        verify(quotaService).release(any(ReleaseQuotaReservationCommand.class));
    }

    @Test
    void apiChatStreamAuthenticatesApiKeyAndCallsAgentRuntime() throws Exception {
        when(apiKeyService.authenticate("sk-valid")).thenReturn(new ApiKeyAuthResult(1L, 20001L, 10001L));
        when(quotaService.reserve(any(ReserveQuotaCommand.class))).thenReturn(reservation());
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

        ArgumentCaptor<StartTraceRootCommand> startCaptor = ArgumentCaptor.forClass(StartTraceRootCommand.class);
        verify(traceService).startRoot(startCaptor.capture());
        assertThat(startCaptor.getValue().tenantId()).isEqualTo(1L);
        assertThat(startCaptor.getValue().entrypoint()).isEqualTo("API_KEY");
    }

    @Test
    void traceServiceFailureDoesNotBreakSse() throws Exception {
        when(quotaService.reserve(any(ReserveQuotaCommand.class))).thenReturn(reservation());
        doThrow(new IllegalStateException("trace down"))
                .when(traceService).startRoot(any(StartTraceRootCommand.class));
        doThrow(new IllegalStateException("trace down"))
                .when(traceService).finishRoot(any(FinishTraceRootCommand.class));
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: done")));
    }

    @Test
    void quotaExceededReturns429AndDoesNotCallAgentRuntime() throws Exception {
        doThrow(new BizException(ErrorCode.QUOTA_EXCEEDED, "Token quota exceeded"))
                .when(quotaService).reserve(any(ReserveQuotaCommand.class));

        mockMvc.perform(post("/internal/ai/chat/stream")
                        .header("X-Internal-Token", "dev-internal-token")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(internalRequest())))
                .andExpect(status().isTooManyRequests());

        verify(agentRuntimeService, never()).run(any(AgentRunCommand.class));
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

    private QuotaReservationDTO reservation() {
        return new QuotaReservationDTO(
                "tr_1",
                1L,
                20001L,
                10001L,
                1000L,
                null,
                "RESERVED",
                0
        );
    }
}
