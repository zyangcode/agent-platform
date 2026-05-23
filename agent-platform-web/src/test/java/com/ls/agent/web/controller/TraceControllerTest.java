package com.ls.agent.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.QueryTracePageCommand;
import com.ls.agent.core.trace.dto.TokenUsageDTO;
import com.ls.agent.core.trace.dto.TraceDetailDTO;
import com.ls.agent.core.trace.dto.TraceSpanDTO;
import com.ls.agent.core.trace.dto.TraceSummaryDTO;
import com.ls.agent.web.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TraceController.class,
        properties = {
                "security.jwt.secret=test-secret-test-secret-test-secret-test",
                "security.jwt.expires-in-seconds=7200"
        }
)
@Import(WebMvcTestSupport.class)
class TraceControllerTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private TraceService traceService;

    @Test
    void getTraceDelegatesWithCurrentUserAndTraceId() throws Exception {
        when(traceService.getTrace(1L, 10001L, "tr_abc")).thenReturn(traceDetail());

        mockMvc.perform(get("/api/traces/tr_abc")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.traceId").value("tr_abc"))
                .andExpect(jsonPath("$.data.spans[0].spanName").value("model.invoke"))
                .andExpect(jsonPath("$.data.tokenUsages[0].totalTokens").value(18));

        verify(traceService).getTrace(1L, 10001L, "tr_abc");
    }

    @Test
    void pageTracesDelegatesWithCurrentUserAndFilters() throws Exception {
        when(traceService.pageTraces(org.mockito.ArgumentMatchers.any(QueryTracePageCommand.class)))
                .thenReturn(PageResult.of(List.of(traceSummary()), 2, 10, 1));

        mockMvc.perform(get("/api/traces")
                        .header("Authorization", bearerToken())
                        .param("applicationId", "20001")
                        .param("profileId", "50001")
                        .param("modelConfigId", "30001")
                        .param("status", "SUCCESS")
                        .param("entrypoint", "INTERNAL_WEB")
                        .param("pageNo", "2")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].traceId").value("tr_abc"))
                .andExpect(jsonPath("$.data.records[0].totalTokens").value(18));

        ArgumentCaptor<QueryTracePageCommand> captor = ArgumentCaptor.forClass(QueryTracePageCommand.class);
        verify(traceService).pageTraces(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().userId()).isEqualTo(10001L);
        assertThat(captor.getValue().applicationId()).isEqualTo(20001L);
        assertThat(captor.getValue().profileId()).isEqualTo(50001L);
        assertThat(captor.getValue().modelConfigId()).isEqualTo(30001L);
        assertThat(captor.getValue().status()).isEqualTo("SUCCESS");
        assertThat(captor.getValue().entrypoint()).isEqualTo("INTERNAL_WEB");
        assertThat(captor.getValue().pageNo()).isEqualTo(2);
        assertThat(captor.getValue().pageSize()).isEqualTo(10);
    }

    @Test
    void pageTracesCapsPageSizeAtOneHundred() throws Exception {
        when(traceService.pageTraces(org.mockito.ArgumentMatchers.any(QueryTracePageCommand.class)))
                .thenReturn(PageResult.empty(1, 100));

        mockMvc.perform(get("/api/traces")
                        .header("Authorization", bearerToken())
                        .param("pageSize", "200"))
                .andExpect(status().isOk());

        ArgumentCaptor<QueryTracePageCommand> captor = ArgumentCaptor.forClass(QueryTracePageCommand.class);
        verify(traceService).pageTraces(captor.capture());
        assertThat(captor.getValue().pageSize()).isEqualTo(100);
    }

    private TraceDetailDTO traceDetail() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 23, 10, 0);
        return new TraceDetailDTO(
                "tr_abc",
                1L,
                20001L,
                10001L,
                50001L,
                90001L,
                "req_1",
                "INTERNAL_WEB",
                "SINGLE",
                "SUCCESS",
                null,
                null,
                now,
                now.plusSeconds(1),
                1000L,
                objectMapper.createObjectNode().put("clientIp", "127.0.0.1"),
                List.of(new TraceSpanDTO(
                        1L,
                        "tr_abc",
                        null,
                        "model.invoke",
                        "MODEL",
                        "core",
                        "SUCCESS",
                        now,
                        now.plusNanos(300_000_000),
                        300L,
                        null,
                        null,
                        objectMapper.createObjectNode().put("modelName", "mock-chat"),
                        now
                )),
                List.of(new TokenUsageDTO(
                        1L,
                        "tr_abc",
                        1L,
                        1L,
                        20001L,
                        10001L,
                        50001L,
                        30001L,
                        40001L,
                        "mock-chat",
                        "MOCK",
                        8,
                        10,
                        18,
                        true,
                        now
                ))
        );
    }

    private TraceSummaryDTO traceSummary() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 23, 10, 0);
        return new TraceSummaryDTO(
                "tr_abc",
                20001L,
                10001L,
                50001L,
                90001L,
                "INTERNAL_WEB",
                "SINGLE",
                "SUCCESS",
                1000L,
                18,
                true,
                now,
                now.plusSeconds(1)
        );
    }

    private String bearerToken() {
        CurrentUserDTO user = new CurrentUserDTO(10001L, 1L, "alice", "Alice", List.of("USER"));
        return "Bearer " + jwtTokenService.generate(user);
    }
}
