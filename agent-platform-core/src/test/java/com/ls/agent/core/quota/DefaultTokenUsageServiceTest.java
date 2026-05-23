package com.ls.agent.core.quota;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.quota.command.QueryTokenUsagePageCommand;
import com.ls.agent.core.quota.command.QueryTokenUsageSummaryCommand;
import com.ls.agent.core.quota.application.DefaultTokenUsageService;
import com.ls.agent.core.quota.command.RecordTokenUsageCommand;
import com.ls.agent.core.quota.dto.TokenUsageDTO;
import com.ls.agent.core.quota.dto.TokenUsageSummaryDTO;
import com.ls.agent.core.quota.entity.TokenUsageLogEntity;
import com.ls.agent.core.quota.mapper.TokenUsageLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultTokenUsageServiceTest {

    private final TokenUsageLogMapper tokenUsageMapper = mock(TokenUsageLogMapper.class);
    private final DefaultTokenUsageService service = new DefaultTokenUsageService(tokenUsageMapper);

    @Test
    void recordInsertsTokenUsageLog() {
        service.record(new RecordTokenUsageCommand(
                "tr_1",
                100L,
                1L,
                20001L,
                10001L,
                50001L,
                30001L,
                1L,
                "mock-chat",
                "MOCK",
                2,
                3,
                5,
                true
        ));

        ArgumentCaptor<TokenUsageLogEntity> captor = ArgumentCaptor.forClass(TokenUsageLogEntity.class);
        verify(tokenUsageMapper).insert(captor.capture());
        assertThat(captor.getValue().getTraceId()).isEqualTo("tr_1");
        assertThat(captor.getValue().getModelName()).isEqualTo("mock-chat");
        assertThat(captor.getValue().getTotalTokens()).isEqualTo(5);
        assertThat(captor.getValue().getEstimated()).isTrue();
    }

    @Test
    void mapperFailureDoesNotEscapeTokenUsageService() {
        doThrow(new IllegalStateException("db down")).when(tokenUsageMapper).insert(any(TokenUsageLogEntity.class));

        assertThatCode(() -> service.record(new RecordTokenUsageCommand(
                "tr_1",
                null,
                1L,
                null,
                null,
                null,
                30001L,
                1L,
                "mock-chat",
                "MOCK",
                0,
                0,
                0,
                true
        ))).doesNotThrowAnyException();
    }

    @Test
    void pageTokenUsagesAppliesFiltersAndReturnsPageResult() {
        Page<TokenUsageLogEntity> page = Page.of(2, 10);
        page.setTotal(1);
        page.setRecords(List.of(tokenUsage(1L, 20001L, 30001L, 40001L, "mock-chat", "MOCK", 8, 10, true)));
        when(tokenUsageMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        PageResult<TokenUsageDTO> result = service.pageTokenUsages(new QueryTokenUsagePageCommand(
                1L,
                10001L,
                20001L,
                30001L,
                40001L,
                2,
                10
        ));

        assertThat(result.pageNo()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(10);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).traceId()).isEqualTo("tr_1");
        assertThat(result.records().get(0).totalTokens()).isEqualTo(18);
    }

    @Test
    void summarizeTokenUsagesAggregatesTotalAndTopModels() {
        when(tokenUsageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                tokenUsage(1L, 20001L, 30001L, 40001L, "mock-chat", "MOCK", 8, 10, true),
                tokenUsage(2L, 20001L, 30001L, 40001L, "mock-chat", "MOCK", 4, 6, false),
                tokenUsage(3L, 20001L, 30002L, 40002L, "qwen-plus", "OPENAI_COMPATIBLE", 20, 30, false)
        ));

        TokenUsageSummaryDTO result = service.summarizeTokenUsages(new QueryTokenUsageSummaryCommand(
                1L,
                10001L,
                20001L,
                LocalDateTime.of(2026, 5, 23, 0, 0),
                LocalDateTime.of(2026, 5, 24, 0, 0)
        ));

        assertThat(result.applicationId()).isEqualTo(20001L);
        assertThat(result.promptTokens()).isEqualTo(32);
        assertThat(result.completionTokens()).isEqualTo(46);
        assertThat(result.totalTokens()).isEqualTo(78);
        assertThat(result.requestCount()).isEqualTo(3);
        assertThat(result.estimatedCount()).isEqualTo(1);
        assertThat(result.realUsageCount()).isEqualTo(2);
        assertThat(result.topModels()).hasSize(2);
        assertThat(result.topModels().get(0).modelConfigId()).isEqualTo(30002L);
        assertThat(result.topModels().get(0).totalTokens()).isEqualTo(50);
    }

    @Test
    void pageTokenUsagesClampsPageSize() {
        Page<TokenUsageLogEntity> page = Page.of(1, 100);
        page.setTotal(0);
        when(tokenUsageMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        PageResult<TokenUsageDTO> result = service.pageTokenUsages(new QueryTokenUsagePageCommand(
                1L,
                10001L,
                null,
                null,
                null,
                0,
                200
        ));

        assertThat(result.pageNo()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(100);
    }

    private TokenUsageLogEntity tokenUsage(
            Long id,
            Long applicationId,
            Long modelConfigId,
            Long providerId,
            String modelName,
            String providerType,
            int promptTokens,
            int completionTokens,
            boolean estimated
    ) {
        TokenUsageLogEntity usage = new TokenUsageLogEntity();
        usage.setId(id);
        usage.setTraceId("tr_" + id);
        usage.setSpanId(100L + id);
        usage.setTenantId(1L);
        usage.setApplicationId(applicationId);
        usage.setUserId(10001L);
        usage.setProfileId(50001L);
        usage.setModelConfigId(modelConfigId);
        usage.setProviderId(providerId);
        usage.setModelName(modelName);
        usage.setProviderType(providerType);
        usage.setPromptTokens(promptTokens);
        usage.setCompletionTokens(completionTokens);
        usage.setTotalTokens(promptTokens + completionTokens);
        usage.setEstimated(estimated);
        usage.setCreatedAt(LocalDateTime.of(2026, 5, 23, 10, 0).plusMinutes(id));
        return usage;
    }
}
