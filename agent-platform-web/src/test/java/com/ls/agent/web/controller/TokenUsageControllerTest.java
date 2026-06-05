package com.ls.agent.web.controller;

import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.core.quota.api.TokenUsageService;
import com.ls.agent.core.quota.command.QueryTokenUsagePageCommand;
import com.ls.agent.core.quota.command.QueryTokenUsageSummaryCommand;
import com.ls.agent.core.quota.dto.TokenUsageDTO;
import com.ls.agent.core.quota.dto.TokenUsageSummaryDTO;
import com.ls.agent.core.quota.dto.TokenUsageTopModelDTO;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TokenUsageController.class,
        properties = {
                "security.jwt.secret=test-secret-test-secret-test-secret-test",
                "security.jwt.expires-in-seconds=7200"
        }
)
@Import(WebMvcTestSupport.class)
class TokenUsageControllerTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private TokenUsageService tokenUsageService;

    @Test
    void pageTokenUsagesDelegatesWithCurrentUserAndFilters() throws Exception {
        when(tokenUsageService.pageTokenUsages(any(QueryTokenUsagePageCommand.class)))
                .thenReturn(PageResult.of(List.of(tokenUsage()), 2, 10, 1));

        mockMvc.perform(get("/api/token-usages")
                        .header("Authorization", bearerToken())
                        .param("applicationId", "20001")
                        .param("modelConfigId", "30001")
                        .param("providerId", "40001")
                        .param("pageNo", "2")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].traceId").value("tr_1"))
                .andExpect(jsonPath("$.data.records[0].totalTokens").value(18));

        ArgumentCaptor<QueryTokenUsagePageCommand> captor = ArgumentCaptor.forClass(QueryTokenUsagePageCommand.class);
        verify(tokenUsageService).pageTokenUsages(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().userId()).isEqualTo(10001L);
        assertThat(captor.getValue().applicationId()).isEqualTo(20001L);
        assertThat(captor.getValue().modelConfigId()).isEqualTo(30001L);
        assertThat(captor.getValue().providerId()).isEqualTo(40001L);
        assertThat(captor.getValue().pageNo()).isEqualTo(2);
        assertThat(captor.getValue().pageSize()).isEqualTo(10);
    }

    @Test
    void pageTokenUsagesCapsPageSizeAtOneHundred() throws Exception {
        when(tokenUsageService.pageTokenUsages(any(QueryTokenUsagePageCommand.class)))
                .thenReturn(PageResult.empty(1, 100));

        mockMvc.perform(get("/api/token-usages")
                        .header("Authorization", bearerToken())
                        .param("pageSize", "200"))
                .andExpect(status().isOk());

        ArgumentCaptor<QueryTokenUsagePageCommand> captor = ArgumentCaptor.forClass(QueryTokenUsagePageCommand.class);
        verify(tokenUsageService).pageTokenUsages(captor.capture());
        assertThat(captor.getValue().pageSize()).isEqualTo(100);
    }

    @Test
    void pageTokenUsagesNormalizesInvalidPaginationAtWebBoundary() throws Exception {
        when(tokenUsageService.pageTokenUsages(any(QueryTokenUsagePageCommand.class)))
                .thenReturn(PageResult.empty(1, 20));

        mockMvc.perform(get("/api/token-usages")
                        .header("Authorization", bearerToken())
                        .param("pageNo", "0")
                        .param("pageSize", "0"))
                .andExpect(status().isOk());

        ArgumentCaptor<QueryTokenUsagePageCommand> captor = ArgumentCaptor.forClass(QueryTokenUsagePageCommand.class);
        verify(tokenUsageService).pageTokenUsages(captor.capture());
        assertThat(captor.getValue().pageNo()).isEqualTo(1);
        assertThat(captor.getValue().pageSize()).isEqualTo(20);
    }

    @Test
    void summarizeTokenUsagesDelegatesWithCurrentUserAndDateRange() throws Exception {
        when(tokenUsageService.summarizeTokenUsages(any(QueryTokenUsageSummaryCommand.class)))
                .thenReturn(new TokenUsageSummaryDTO(
                        20001L,
                        12,
                        16,
                        28,
                        2,
                        1,
                        1,
                        List.of(new TokenUsageTopModelDTO(30001L, "mock-chat", "MOCK", 2, 28))
                ));

        mockMvc.perform(get("/api/token-usages/summary")
                        .header("Authorization", bearerToken())
                        .param("applicationId", "20001")
                        .param("startedFrom", "2026-05-23T00:00:00")
                        .param("startedTo", "2026-05-24T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTokens").value(28))
                .andExpect(jsonPath("$.data.topModels[0].modelName").value("mock-chat"));

        ArgumentCaptor<QueryTokenUsageSummaryCommand> captor = ArgumentCaptor.forClass(QueryTokenUsageSummaryCommand.class);
        verify(tokenUsageService).summarizeTokenUsages(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().userId()).isEqualTo(10001L);
        assertThat(captor.getValue().applicationId()).isEqualTo(20001L);
        assertThat(captor.getValue().startedFrom()).isEqualTo(LocalDateTime.of(2026, 5, 23, 0, 0));
        assertThat(captor.getValue().startedTo()).isEqualTo(LocalDateTime.of(2026, 5, 24, 0, 0));
    }

    private TokenUsageDTO tokenUsage() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 23, 10, 0);
        return new TokenUsageDTO(
                1L,
                "tr_1",
                100L,
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
        );
    }

    private String bearerToken() {
        CurrentUserDTO user = new CurrentUserDTO(10001L, 1L, "alice", "Alice", List.of("USER"));
        return "Bearer " + jwtTokenService.generate(user);
    }
}
