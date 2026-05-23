package com.ls.agent.core.trace;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.quota.api.TokenUsageService;
import com.ls.agent.core.quota.dto.TokenUsageAggregateDTO;
import com.ls.agent.core.quota.dto.TokenUsageDTO;
import com.ls.agent.core.trace.application.DefaultTraceService;
import com.ls.agent.core.trace.command.FinishTraceRootCommand;
import com.ls.agent.core.trace.command.FinishTraceSpanCommand;
import com.ls.agent.core.trace.command.QueryTracePageCommand;
import com.ls.agent.core.trace.command.StartTraceRootCommand;
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceDetailDTO;
import com.ls.agent.core.trace.dto.TraceSummaryDTO;
import com.ls.agent.core.trace.entity.TraceRootEntity;
import com.ls.agent.core.trace.entity.TraceSpanEntity;
import com.ls.agent.core.trace.mapper.TraceRootMapper;
import com.ls.agent.core.trace.mapper.TraceSpanMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultTraceServiceTest {

    private final TraceRootMapper rootMapper = mock(TraceRootMapper.class);
    private final TraceSpanMapper spanMapper = mock(TraceSpanMapper.class);
    private final TokenUsageService tokenUsageService = mock(TokenUsageService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-23T10:00:00Z"), ZoneOffset.UTC);
    private final DefaultTraceService service = new DefaultTraceService(
            rootMapper,
            spanMapper,
            tokenUsageService,
            objectMapper,
            clock
    );

    @Test
    void startRootInsertsRunningTraceRoot() {
        service.startRoot(new StartTraceRootCommand(
                "tr_1",
                1L,
                20001L,
                10001L,
                50001L,
                null,
                "client-1",
                "INTERNAL_WEB",
                "agent",
                objectMapper.createObjectNode().put("stream", true)
        ));

        ArgumentCaptor<TraceRootEntity> captor = ArgumentCaptor.forClass(TraceRootEntity.class);
        verify(rootMapper).insert(captor.capture());
        assertThat(captor.getValue().getTraceId()).isEqualTo("tr_1");
        assertThat(captor.getValue().getStatus()).isEqualTo("RUNNING");
        assertThat(captor.getValue().getStartedAt()).isNotNull();
        assertThat(captor.getValue().getMetadata().get("stream").asBoolean()).isTrue();
    }

    @Test
    void finishRootUpdatesStatusAndLatencyWithoutThrowing() {
        service.finishRoot(new FinishTraceRootCommand(
                "tr_1",
                90001L,
                "SUCCESS",
                null,
                null
        ));

        ArgumentCaptor<TraceRootEntity> entityCaptor = ArgumentCaptor.forClass(TraceRootEntity.class);
        verify(rootMapper).update(entityCaptor.capture(), any(LambdaUpdateWrapper.class));
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(entityCaptor.getValue().getConversationId()).isEqualTo(90001L);
        assertThat(entityCaptor.getValue().getEndedAt()).isNotNull();
        assertThat(entityCaptor.getValue().getLatencyMs()).isNotNegative();
    }

    @Test
    void startSpanAndFinishSpanUseTraceSpanMapper() {
        service.startSpan(new StartTraceSpanCommand(
                "tr_1",
                null,
                "model.invoke",
                "MODEL",
                "core",
                objectMapper.createObjectNode().put("step", 1)
        ));

        ArgumentCaptor<TraceSpanEntity> spanCaptor = ArgumentCaptor.forClass(TraceSpanEntity.class);
        verify(spanMapper).insert(spanCaptor.capture());
        assertThat(spanCaptor.getValue().getStatus()).isEqualTo("RUNNING");
        assertThat(spanCaptor.getValue().getAttributes().get("step").asInt()).isEqualTo(1);

        service.finishSpan(new FinishTraceSpanCommand(100L, "SUCCESS", null, null));

        ArgumentCaptor<TraceSpanEntity> finishCaptor = ArgumentCaptor.forClass(TraceSpanEntity.class);
        verify(spanMapper).update(finishCaptor.capture(), any(LambdaUpdateWrapper.class));
        assertThat(finishCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(finishCaptor.getValue().getLatencyMs()).isNotNegative();
    }

    @Test
    void mapperFailureDoesNotEscapeTraceService() {
        doThrow(new IllegalStateException("db down")).when(rootMapper).insert(any(TraceRootEntity.class));
        doThrow(new IllegalStateException("db down")).when(spanMapper).insert(any(TraceSpanEntity.class));

        assertThatCode(() -> service.startRoot(new StartTraceRootCommand(
                "tr_1",
                1L,
                null,
                null,
                null,
                null,
                null,
                "API_KEY",
                "none",
                null
        ))).doesNotThrowAnyException();

        assertThatCode(() -> service.startSpan(new StartTraceSpanCommand(
                "tr_1",
                null,
                "context.build",
                "CONTEXT",
                "core",
                null
        ))).doesNotThrowAnyException();
    }

    @Test
    void getTraceReturnsOwnedRootSpansAndTokenUsages() {
        when(rootMapper.selectOne(any())).thenReturn(traceRoot(1L, 10001L));
        when(spanMapper.selectList(any())).thenReturn(List.of(traceSpan(10L, "context.build")));
        when(tokenUsageService.listByTrace(1L, 10001L, "tr_1")).thenReturn(List.of(tokenUsage(20L, 128)));

        TraceDetailDTO result = service.getTrace(1L, 10001L, "tr_1");

        assertThat(result.traceId()).isEqualTo("tr_1");
        assertThat(result.tenantId()).isEqualTo(1L);
        assertThat(result.userId()).isEqualTo(10001L);
        assertThat(result.spans()).extracting("spanName").containsExactly("context.build");
        assertThat(result.tokenUsages()).extracting("totalTokens").containsExactly(128);
    }

    @Test
    void getTraceRejectsTraceFromAnotherTenantOrUser() {
        when(rootMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getTrace(1L, 10001L, "tr_1"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void pageTracesAppliesFiltersAndAggregatesTokenUsage() {
        Page<TraceRootEntity> page = Page.of(2, 20);
        page.setTotal(1);
        page.setRecords(List.of(traceRoot(1L, 10001L)));
        when(rootMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        when(tokenUsageService.aggregateByTraceIds(1L, 10001L, List.of("tr_1"), null))
                .thenReturn(Map.of("tr_1", new TokenUsageAggregateDTO(160, false)));

        PageResult<TraceSummaryDTO> result = service.pageTraces(new QueryTracePageCommand(
                1L,
                10001L,
                20001L,
                50001L,
                null,
                "SUCCESS",
                "INTERNAL_WEB",
                2,
                20
        ));

        assertThat(result.pageNo()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(20);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).traceId()).isEqualTo("tr_1");
        assertThat(result.records().get(0).totalTokens()).isEqualTo(160);
        assertThat(result.records().get(0).estimated()).isFalse();
    }

    @Test
    void pageTracesClampsPageSize() {
        Page<TraceRootEntity> page = Page.of(1, 100);
        page.setTotal(0);
        when(rootMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        PageResult<TraceSummaryDTO> result = service.pageTraces(new QueryTracePageCommand(
                1L,
                10001L,
                null,
                null,
                null,
                null,
                null,
                0,
                200
        ));

        assertThat(result.pageNo()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(100);
    }

    private TraceRootEntity traceRoot(Long tenantId, Long userId) {
        TraceRootEntity root = new TraceRootEntity();
        root.setTraceId("tr_1");
        root.setTenantId(tenantId);
        root.setApplicationId(20001L);
        root.setUserId(userId);
        root.setProfileId(50001L);
        root.setConversationId(90001L);
        root.setClientRequestId("client-1");
        root.setEntrypoint("INTERNAL_WEB");
        root.setAgentMode("agent");
        root.setStatus("SUCCESS");
        root.setStartedAt(LocalDateTime.of(2026, 5, 23, 10, 0));
        root.setEndedAt(LocalDateTime.of(2026, 5, 23, 10, 0, 1));
        root.setLatencyMs(1000L);
        root.setMetadata(objectMapper.createObjectNode().put("stream", true));
        return root;
    }

    private TraceSpanEntity traceSpan(Long id, String spanName) {
        TraceSpanEntity span = new TraceSpanEntity();
        span.setId(id);
        span.setTraceId("tr_1");
        span.setSpanName(spanName);
        span.setSpanType("CONTEXT");
        span.setComponent("core");
        span.setStatus("SUCCESS");
        span.setStartedAt(LocalDateTime.of(2026, 5, 23, 10, 0));
        span.setEndedAt(LocalDateTime.of(2026, 5, 23, 10, 0, 1));
        span.setLatencyMs(1000L);
        span.setAttributes(objectMapper.createObjectNode().put("step", 1));
        return span;
    }

    private TokenUsageDTO tokenUsage(Long id, int totalTokens) {
        return new TokenUsageDTO(
                id,
                "tr_1",
                10L,
                1L,
                20001L,
                10001L,
                50001L,
                30001L,
                40001L,
                "mock-chat",
                "mock",
                totalTokens / 2,
                totalTokens / 2,
                totalTokens,
                false,
                null
        );
    }
}
