package com.ls.agent.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.api.AgentRuntimeService;
import com.ls.agent.core.agent.command.AgentRunCommand;
import com.ls.agent.core.agent.dto.AgentRunResult;
import com.ls.agent.core.agent.dto.AgentToolEventDTO;
import com.ls.agent.core.alert.api.AlertEventService;
import com.ls.agent.core.alert.command.RecordAlertEventCommand;
import com.ls.agent.core.identity.api.ApiKeyService;
import com.ls.agent.core.identity.dto.ApiKeyAuthResult;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.quota.api.QuotaService;
import com.ls.agent.core.quota.command.CommitQuotaReservationCommand;
import com.ls.agent.core.quota.api.TokenUsageService;
import com.ls.agent.core.quota.command.RecordTokenUsageCommand;
import com.ls.agent.core.quota.command.ReleaseQuotaReservationCommand;
import com.ls.agent.core.quota.command.ReserveQuotaCommand;
import com.ls.agent.core.quota.dto.QuotaReservationDTO;
import com.ls.agent.core.security.api.SecurityEventService;
import com.ls.agent.core.security.api.SensitiveDataScanner;
import com.ls.agent.core.security.command.RecordSecurityEventCommand;
import com.ls.agent.core.security.dto.SensitiveDataFindingDTO;
import com.ls.agent.core.team.api.TeamEventSink;
import com.ls.agent.core.team.dto.TeamRuntimeEventDTO;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.FinishTraceRootCommand;
import com.ls.agent.core.trace.command.FinishTraceSpanCommand;
import com.ls.agent.core.trace.command.StartTraceRootCommand;
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceSpanDTO;
import com.ls.agent.gateway.application.DirectModelRunService;
import com.ls.agent.gateway.dto.GatewayChatRequest;
import com.ls.agent.gateway.filter.AlertFilter;
import com.ls.agent.gateway.filter.QuotaFilter;
import com.ls.agent.gateway.filter.SensitiveDataFilter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
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
@Import({QuotaFilter.class, SensitiveDataFilter.class, AlertFilter.class, DirectModelRunService.class})
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

    @MockBean
    private TokenUsageService tokenUsageService;

    @MockBean
    private SensitiveDataScanner sensitiveDataScanner;

    @MockBean
    private SecurityEventService securityEventService;

    @MockBean
    private AlertEventService alertEventService;

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
        when(sensitiveDataScanner.scan("hello", "REQUEST_MESSAGE")).thenReturn(List.of());
        when(quotaService.reserve(any(ReserveQuotaCommand.class))).thenReturn(reservation());
        when(agentRuntimeService.run(any(AgentRunCommand.class), any(TeamEventSink.class), any())).thenReturn(agentResult());

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
        verify(agentRuntimeService).run(captor.capture(), any(TeamEventSink.class), any());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().applicationId()).isEqualTo(20001L);
        assertThat(captor.getValue().userId()).isEqualTo(10001L);
        assertThat(captor.getValue().profileId()).isEqualTo(50001L);
        assertThat(captor.getValue().selectedSkillIds()).containsExactly(60001L);
        assertThat(captor.getValue().confirmedToolKeys()).containsExactly("skill:deploy");

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
    void internalChatStreamPreservesExplicitEmptyToolSelections() throws Exception {
        when(sensitiveDataScanner.scan("hello", "REQUEST_MESSAGE")).thenReturn(List.of());
        when(quotaService.reserve(any(ReserveQuotaCommand.class))).thenReturn(reservation());
        when(agentRuntimeService.run(any(AgentRunCommand.class), any(TeamEventSink.class), any())).thenReturn(agentResult());

        String body = """
                {
                  "tenantId": 1,
                  "applicationId": 20001,
                  "userId": 10001,
                  "channel": "WEB",
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

        var result = mockMvc.perform(post("/internal/ai/chat/stream")
                        .header("X-Internal-Token", "dev-internal-token")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: message")));

        ArgumentCaptor<AgentRunCommand> captor = ArgumentCaptor.forClass(AgentRunCommand.class);
        verify(agentRuntimeService).run(captor.capture(), any(TeamEventSink.class), any());
        assertThat(captor.getValue().selectedSkillIds()).isEmpty();
        assertThat(captor.getValue().selectedMcpToolIds()).isEmpty();
    }

    @Test
    void internalChatStreamEmitsRuntimeToolEventsBeforeFinalMessage() throws Exception {
        when(sensitiveDataScanner.scan("hello", "REQUEST_MESSAGE")).thenReturn(List.of());
        when(quotaService.reserve(any(ReserveQuotaCommand.class))).thenReturn(reservation());
        when(agentRuntimeService.run(any(AgentRunCommand.class), any(TeamEventSink.class), any())).thenReturn(agentResultWithToolEvents());

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: action")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: observation")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: tool_confirm_required")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"toolType\":\"skill\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"toolName\":\"calculator\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"toolName\":\"deploy\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"toolKey\":\"skill:deploy\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: message")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: done")));
    }

    @Test
    void internalChatStreamEmitsMessageDeltaBeforeFinalMessageWhenRuntimeStreamsTokens() throws Exception {
        when(sensitiveDataScanner.scan("hello", "REQUEST_MESSAGE")).thenReturn(List.of());
        when(quotaService.reserve(any(ReserveQuotaCommand.class))).thenReturn(reservation());
        doAnswer(invocation -> {
            com.ls.agent.core.model.api.ModelStreamCallback callback = invocation.getArgument(2);
            callback.onToken("hel");
            callback.onToken("lo");
            return new AgentRunResult(
                    90001L,
                    "hello",
                    new ModelUsageDTO(1, 2, 3, true)
            );
        }).when(agentRuntimeService).run(any(AgentRunCommand.class), any(TeamEventSink.class), any());

        var result = mockMvc.perform(post("/internal/ai/chat/stream")
                        .header("X-Internal-Token", "dev-internal-token")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(internalRequest())))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: message_delta")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"content\":\"hel\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"content\":\"lo\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: message")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: done")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body.indexOf("event: message_delta")).isLessThan(body.indexOf("event: message\n"));
        for (JsonNode deltaPayload : eventPayloads(body, "message_delta")) {
            assertThat(deltaPayload.get("traceId").asText()).isNotBlank();
            assertThat(deltaPayload.get("conversationId").asLong()).isEqualTo(90001L);
            assertThat(deltaPayload.get("step").asInt()).isGreaterThanOrEqualTo(2);
        }
        verify(agentRuntimeService).run(any(AgentRunCommand.class), any(TeamEventSink.class), any());
    }

    @Test
    void internalChatStreamEmitsToolEventsBeforeBufferedMessageDeltaWhenRuntimeStreamsAfterToolUse() throws Exception {
        when(sensitiveDataScanner.scan("hello", "REQUEST_MESSAGE")).thenReturn(List.of());
        when(quotaService.reserve(any(ReserveQuotaCommand.class))).thenReturn(reservation());
        doAnswer(invocation -> {
            com.ls.agent.core.model.api.ModelStreamCallback callback = invocation.getArgument(2);
            callback.onToken("final answer after tool");
            return agentResultWithToolEvents();
        }).when(agentRuntimeService).run(any(AgentRunCommand.class), any(TeamEventSink.class), any());

        var result = mockMvc.perform(post("/internal/ai/chat/stream")
                        .header("X-Internal-Token", "dev-internal-token")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(internalRequest())))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: action")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: observation")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: message_delta")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"content\":\"final answer after tool\"")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body.indexOf("event: action")).isLessThan(body.indexOf("event: message_delta"));
        assertThat(body.indexOf("event: observation")).isLessThan(body.indexOf("event: message_delta"));
        assertThat(body.indexOf("event: message_delta")).isLessThan(body.indexOf("event: message\n"));
    }

    @Test
    void internalChatStreamWritesTeamRuntimeEventsBeforeFinalMessage() throws Exception {
        when(sensitiveDataScanner.scan("hello", "REQUEST_MESSAGE")).thenReturn(List.of());
        when(quotaService.reserve(any(ReserveQuotaCommand.class))).thenReturn(reservation());
        when(agentRuntimeService.run(any(AgentRunCommand.class), any(TeamEventSink.class), any()))
                .thenAnswer(invocation -> {
                    TeamEventSink sink = invocation.getArgument(1);
                    sink.emit(TeamRuntimeEventDTO.plan(
                            "tr_team",
                            2,
                            "Planner generated task plan",
                            objectMapper.createObjectNode().put("goal", "demo")
                    ));
                    return agentResult();
                });

        var result = mockMvc.perform(post("/internal/ai/chat/stream")
                        .header("X-Internal-Token", "dev-internal-token")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(internalRequest())))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: team_plan")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"team_plan\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"role\":\"PLANNER\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"payload\":{\"goal\":\"demo\"}")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: message")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: done")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body.indexOf("event: team_plan")).isLessThan(body.indexOf("event: message"));
        verify(agentRuntimeService).run(any(AgentRunCommand.class), any(TeamEventSink.class), any());
        verify(agentRuntimeService, never()).run(any(AgentRunCommand.class));
    }

    @Test
    void internalChatStreamAcceptsUppercaseTeamMode() throws Exception {
        when(sensitiveDataScanner.scan("hello", "REQUEST_MESSAGE")).thenReturn(List.of());
        when(quotaService.reserve(any(ReserveQuotaCommand.class))).thenReturn(reservation());
        when(agentRuntimeService.run(any(AgentRunCommand.class), any(TeamEventSink.class), any())).thenReturn(agentResult());

        GatewayChatRequest request = new GatewayChatRequest(
                1L,
                20001L,
                10001L,
                "WEB",
                "TEAM",
                50001L,
                90001L,
                "hello",
                List.of(60001L),
                List.of(70001L),
                "req-1",
                80001L,
                null,
                true,
                List.of()
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: message")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: done")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("agentMode is invalid"))));

        ArgumentCaptor<StartTraceRootCommand> startCaptor = ArgumentCaptor.forClass(StartTraceRootCommand.class);
        verify(traceService).startRoot(startCaptor.capture());
        assertThat(startCaptor.getValue().agentMode()).isEqualTo("TEAM");
        verify(agentRuntimeService).run(any(AgentRunCommand.class), any(TeamEventSink.class), any());
    }

    @Test
    void internalChatStreamDefaultsToAgentMode() throws Exception {
        when(sensitiveDataScanner.scan("hello", "REQUEST_MESSAGE")).thenReturn(List.of());
        when(quotaService.reserve(any(ReserveQuotaCommand.class))).thenReturn(reservation());
        when(agentRuntimeService.run(any(AgentRunCommand.class), any(TeamEventSink.class), any())).thenReturn(agentResult());

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
                null,
                List.of()
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

        verify(agentRuntimeService).run(any(AgentRunCommand.class), any(TeamEventSink.class), any());
    }

    @Test
    void internalChatStreamConvertsRuntimeFailureToSseErrorEvent() throws Exception {
        when(sensitiveDataScanner.scan("hello", "REQUEST_MESSAGE")).thenReturn(List.of());
        when(quotaService.reserve(any(ReserveQuotaCommand.class))).thenReturn(reservation());
        doThrow(new BizException(ErrorCode.REQUEST_INVALID, "Agent failed"))
                .when(agentRuntimeService).run(any(AgentRunCommand.class), any(TeamEventSink.class), any());

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
        ArgumentCaptor<RecordAlertEventCommand> alertCaptor =
                ArgumentCaptor.forClass(RecordAlertEventCommand.class);
        verify(alertEventService).record(alertCaptor.capture());
        assertThat(alertCaptor.getValue().alertType()).isEqualTo("MODEL_ERROR");
        assertThat(alertCaptor.getValue().level()).isEqualTo("ERROR");
        assertThat(alertCaptor.getValue().tenantId()).isEqualTo(1L);
    }

    @Test
    void apiChatStreamAuthenticatesApiKeyAndCallsAgentRuntime() throws Exception {
        when(apiKeyService.authenticate("sk-valid")).thenReturn(new ApiKeyAuthResult(1L, 20001L, 10001L));
        when(sensitiveDataScanner.scan("hello", "REQUEST_MESSAGE")).thenReturn(List.of());
        when(quotaService.reserve(any(ReserveQuotaCommand.class))).thenReturn(reservation());
        when(agentRuntimeService.run(any(AgentRunCommand.class), any(TeamEventSink.class), any())).thenReturn(agentResult());

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
        verify(agentRuntimeService).run(any(AgentRunCommand.class), any(TeamEventSink.class), any());

        ArgumentCaptor<StartTraceRootCommand> startCaptor = ArgumentCaptor.forClass(StartTraceRootCommand.class);
        verify(traceService).startRoot(startCaptor.capture());
        assertThat(startCaptor.getValue().tenantId()).isEqualTo(1L);
        assertThat(startCaptor.getValue().entrypoint()).isEqualTo("API_KEY");
    }

    @Test
    void traceServiceFailureDoesNotBreakSse() throws Exception {
        when(sensitiveDataScanner.scan("hello", "REQUEST_MESSAGE")).thenReturn(List.of());
        when(quotaService.reserve(any(ReserveQuotaCommand.class))).thenReturn(reservation());
        doThrow(new IllegalStateException("trace down"))
                .when(traceService).startRoot(any(StartTraceRootCommand.class));
        doThrow(new IllegalStateException("trace down"))
                .when(traceService).finishRoot(any(FinishTraceRootCommand.class));
        when(agentRuntimeService.run(any(AgentRunCommand.class), any(TeamEventSink.class), any())).thenReturn(agentResult());

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
        when(sensitiveDataScanner.scan("hello", "REQUEST_MESSAGE")).thenReturn(List.of());
        doThrow(new BizException(ErrorCode.QUOTA_EXCEEDED, "Token quota exceeded"))
                .when(quotaService).reserve(any(ReserveQuotaCommand.class));

        mockMvc.perform(post("/internal/ai/chat/stream")
                        .header("X-Internal-Token", "dev-internal-token")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(internalRequest())))
                .andExpect(status().isTooManyRequests());

        verify(agentRuntimeService, never()).run(any(AgentRunCommand.class));
        ArgumentCaptor<RecordAlertEventCommand> alertCaptor =
                ArgumentCaptor.forClass(RecordAlertEventCommand.class);
        verify(alertEventService).record(alertCaptor.capture());
        assertThat(alertCaptor.getValue().alertType()).isEqualTo("TOKEN_EXCEEDED");
        assertThat(alertCaptor.getValue().level()).isEqualTo("WARN");
    }

    @Test
    void sensitiveRequestReturns403AndDoesNotReserveQuotaOrCallRuntime() throws Exception {
        when(sensitiveDataScanner.scan("hello", "REQUEST_MESSAGE"))
                .thenReturn(List.of(new SensitiveDataFindingDTO(
                        "PHONE",
                        "REQUEST_MESSAGE",
                        "hash-1",
                        "138****5678"
                )));

        mockMvc.perform(post("/internal/ai/chat/stream")
                        .header("X-Internal-Token", "dev-internal-token")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(internalRequest())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SECURITY_BLOCKED")));

        ArgumentCaptor<RecordSecurityEventCommand> eventCaptor =
                ArgumentCaptor.forClass(RecordSecurityEventCommand.class);
        verify(securityEventService).record(eventCaptor.capture());
        assertThat(eventCaptor.getValue().tenantId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().applicationId()).isEqualTo(20001L);
        assertThat(eventCaptor.getValue().userId()).isEqualTo(10001L);
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("PHONE");
        assertThat(eventCaptor.getValue().location()).isEqualTo("REQUEST_MESSAGE");
        assertThat(eventCaptor.getValue().maskedSample()).isEqualTo("138****5678");
        assertThat(eventCaptor.getValue().action()).isEqualTo("BLOCK");

        verify(quotaService, never()).reserve(any(ReserveQuotaCommand.class));
        verify(agentRuntimeService, never()).run(any(AgentRunCommand.class));
        ArgumentCaptor<RecordAlertEventCommand> alertCaptor =
                ArgumentCaptor.forClass(RecordAlertEventCommand.class);
        verify(alertEventService).record(alertCaptor.capture());
        assertThat(alertCaptor.getValue().alertType()).isEqualTo("SECURITY_BLOCKED");
        assertThat(alertCaptor.getValue().level()).isEqualTo("WARN");
    }

    @Test
    void directModeScansMessagesAndBlocksBeforeModelInvoke() throws Exception {
        GatewayChatRequest request = directRequest(List.of(new ModelMessage("user", "call me at 13812345678")));
        when(sensitiveDataScanner.scan(null, "REQUEST_MESSAGE")).thenReturn(List.of());
        when(sensitiveDataScanner.scan("call me at 13812345678", "REQUEST_MESSAGES"))
                .thenReturn(List.of(new SensitiveDataFindingDTO(
                        "PHONE",
                        "REQUEST_MESSAGES",
                        "hash-3",
                        "138****5678"
                )));

        mockMvc.perform(post("/internal/ai/chat/stream")
                        .header("X-Internal-Token", "dev-internal-token")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SECURITY_BLOCKED")));

        ArgumentCaptor<RecordSecurityEventCommand> eventCaptor =
                ArgumentCaptor.forClass(RecordSecurityEventCommand.class);
        verify(securityEventService).record(eventCaptor.capture());
        assertThat(eventCaptor.getValue().location()).isEqualTo("REQUEST_MESSAGES");

        verify(quotaService, never()).reserve(any(ReserveQuotaCommand.class));
        verify(modelInvokeService, never()).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void directModeRecordsModelSpanAndTokenUsage() throws Exception {
        GatewayChatRequest request = directRequest(List.of(new ModelMessage("user", "hello direct")));
        when(sensitiveDataScanner.scan(null, "REQUEST_MESSAGE")).thenReturn(List.of());
        when(sensitiveDataScanner.scan("hello direct", "REQUEST_MESSAGES")).thenReturn(List.of());
        when(quotaService.reserve(any(ReserveQuotaCommand.class))).thenReturn(reservation());
        when(traceService.startSpan(any(StartTraceSpanCommand.class))).thenReturn(traceSpan());
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult());

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: message")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("direct assistant")));

        ArgumentCaptor<StartTraceSpanCommand> spanCaptor = ArgumentCaptor.forClass(StartTraceSpanCommand.class);
        verify(traceService).startSpan(spanCaptor.capture());
        assertThat(spanCaptor.getValue().spanName()).isEqualTo("model.invoke");
        assertThat(spanCaptor.getValue().spanType()).isEqualTo("MODEL");
        assertThat(spanCaptor.getValue().attributes().get("modelConfigId").asLong()).isEqualTo(30001L);

        ArgumentCaptor<RecordTokenUsageCommand> usageCaptor = ArgumentCaptor.forClass(RecordTokenUsageCommand.class);
        verify(tokenUsageService).record(usageCaptor.capture());
        assertThat(usageCaptor.getValue().spanId()).isEqualTo(80001L);
        assertThat(usageCaptor.getValue().tenantId()).isEqualTo(1L);
        assertThat(usageCaptor.getValue().applicationId()).isEqualTo(20001L);
        assertThat(usageCaptor.getValue().userId()).isEqualTo(10001L);
        assertThat(usageCaptor.getValue().profileId()).isNull();
        assertThat(usageCaptor.getValue().modelConfigId()).isEqualTo(30001L);
        assertThat(usageCaptor.getValue().totalTokens()).isEqualTo(7);

        verify(traceService).finishSpan(any(FinishTraceSpanCommand.class));
        verify(agentRuntimeService, never()).run(any(AgentRunCommand.class));
    }

    @Test
    void scannerFailureReturns403AndDoesNotReserveQuotaOrCallRuntime() throws Exception {
        doThrow(new IllegalStateException("scanner down"))
                .when(sensitiveDataScanner).scan("hello", "REQUEST_MESSAGE");

        mockMvc.perform(post("/internal/ai/chat/stream")
                        .header("X-Internal-Token", "dev-internal-token")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(internalRequest())))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SECURITY_BLOCKED")));

        verify(securityEventService, never()).record(any(RecordSecurityEventCommand.class));
        verify(quotaService, never()).reserve(any(ReserveQuotaCommand.class));
        verify(agentRuntimeService, never()).run(any(AgentRunCommand.class));
        verify(alertEventService).record(any(RecordAlertEventCommand.class));
    }

    @Test
    void securityEventRecordFailureStillBlocksRequest() throws Exception {
        when(sensitiveDataScanner.scan("hello", "REQUEST_MESSAGE"))
                .thenReturn(List.of(new SensitiveDataFindingDTO(
                        "EMAIL",
                        "REQUEST_MESSAGE",
                        "hash-2",
                        "a***@example.com"
                )));
        doThrow(new IllegalStateException("audit down"))
                .when(securityEventService).record(any(RecordSecurityEventCommand.class));

        mockMvc.perform(post("/internal/ai/chat/stream")
                        .header("X-Internal-Token", "dev-internal-token")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(internalRequest())))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SECURITY_BLOCKED")));

        verify(securityEventService).record(any(RecordSecurityEventCommand.class));
        verify(quotaService, never()).reserve(any(ReserveQuotaCommand.class));
        verify(agentRuntimeService, never()).run(any(AgentRunCommand.class));
        verify(alertEventService).record(any(RecordAlertEventCommand.class));
    }

    @Test
    void alertRecordFailureDoesNotChangeQuotaExceededResponse() throws Exception {
        when(sensitiveDataScanner.scan("hello", "REQUEST_MESSAGE")).thenReturn(List.of());
        doThrow(new BizException(ErrorCode.QUOTA_EXCEEDED, "Token quota exceeded"))
                .when(quotaService).reserve(any(ReserveQuotaCommand.class));
        doThrow(new IllegalStateException("alert down"))
                .when(alertEventService).record(any(RecordAlertEventCommand.class));

        mockMvc.perform(post("/internal/ai/chat/stream")
                        .header("X-Internal-Token", "dev-internal-token")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(internalRequest())))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("QUOTA_EXCEEDED")));

        verify(alertEventService).record(any(RecordAlertEventCommand.class));
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
                null,
                List.of("skill:deploy")
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
                null,
                List.of()
        );
    }

    private GatewayChatRequest directRequest(List<ModelMessage> messages) {
        return new GatewayChatRequest(
                1L,
                20001L,
                10001L,
                "WEB",
                "none",
                null,
                null,
                null,
                null,
                null,
                "client-1",
                30001L,
                messages,
                null,
                List.of()
        );
    }

    private AgentRunResult agentResult() {
        return new AgentRunResult(
                90001L,
                "assistant says hello",
                new ModelUsageDTO(1, 2, 3, true)
        );
    }

    private AgentRunResult agentResultWithToolEvents() {
        return new AgentRunResult(
                90001L,
                "assistant says hello",
                new ModelUsageDTO(1, 2, 3, true),
                List.of(
                        new AgentToolEventDTO("action", "skill", "calculator", "@skill:calculator {\"expression\":\"1+2\"}"),
                        new AgentToolEventDTO("observation", "skill", "calculator", "{\"result\":\"3\"}"),
                        new AgentToolEventDTO("tool_confirm_required", "skill", "deploy", "[tool confirm required] skill:deploy risk=HIGH")
                )
        );
    }

    private ModelInvokeResult modelResult() {
        return new ModelInvokeResult(
                30001L,
                40001L,
                "mock",
                "mock-chat",
                "direct assistant",
                new ModelUsageDTO(3, 4, 7, false)
        );
    }

    private TraceSpanDTO traceSpan() {
        return new TraceSpanDTO(
                80001L,
                "tr_1",
                null,
                "model.invoke",
                "MODEL",
                "core",
                "RUNNING",
                null,
                null,
                null,
                null,
                null,
                objectMapper.createObjectNode(),
                null
        );
    }

    private List<JsonNode> eventPayloads(String body, String eventName) throws Exception {
        List<JsonNode> payloads = new ArrayList<>();
        String[] frames = body.split("\\R\\R");
        for (String frame : frames) {
            if (!frame.startsWith("event: " + eventName)) {
                continue;
            }
            for (String line : frame.split("\\R")) {
                if (line.startsWith("data: ")) {
                    payloads.add(objectMapper.readTree(line.substring("data: ".length())));
                }
            }
        }
        return payloads;
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
