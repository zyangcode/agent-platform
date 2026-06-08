package com.ls.agent.core.context;

import com.ls.agent.core.context.application.RagSlotSource;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.ContextSlot;
import com.ls.agent.core.context.dto.ContextSlotContent;
import com.ls.agent.core.context.dto.ContextSlotKind;
import com.ls.agent.core.rag.api.RagSearchService;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;
import com.ls.agent.core.trace.api.TraceService;
import com.ls.agent.core.trace.command.FinishTraceSpanCommand;
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceSpanDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagSlotSourceTest {

    @Test
    void fetchBuildsRagReferenceBlockWithinBudget() {
        RagSearchService searchService = (tenantId, applicationId, userId, profileId, query, topK) -> List.of(
                new RagSearchResultDTO(10L, 100L, "Basketball", "Low rain supports outdoor basketball.", "", 0.91),
                new RagSearchResultDTO(11L, 101L, "Heat", "Avoid very hot noon hours.", "", 0.82),
                new RagSearchResultDTO(12L, 102L, "Long Reference", "This reference should not fit into the tiny rag budget.", "kb://long", 0.7)
        );
        RagSlotSource source = new RagSlotSource(searchService);

        ContextSlotContent content = source.fetch(
                ContextSlot.of(ContextSlotKind.RAG_RECALL, 32),
                command("is it suitable to play basketball today")
        );

        assertThat(source.supports(ContextSlotKind.RAG_RECALL)).isTrue();
        assertThat(source.supports(ContextSlotKind.TASK_MEMORY)).isFalse();
        assertThat(content.kind()).isEqualTo(ContextSlotKind.RAG_RECALL);
        assertThat(content.content())
                .contains("RAG references:")
                .contains("Basketball")
                .contains("Low rain supports outdoor basketball.")
                .contains("Heat")
                .doesNotContain("Long Reference");
        assertThat(content.usedTokens()).isLessThanOrEqualTo(32);
        assertThat(content.truncated()).isTrue();
    }

    @Test
    void fetchReturnsEmptyContentWhenNoRagResultsExist() {
        RagSlotSource source = new RagSlotSource((tenantId, applicationId, userId, profileId, query, topK) -> List.of());

        ContextSlotContent content = source.fetch(
                ContextSlot.of(ContextSlotKind.RAG_RECALL, 100),
                command("unknown topic")
        );

        assertThat(content.content()).isEmpty();
        assertThat(content.usedTokens()).isZero();
        assertThat(content.truncated()).isFalse();
    }

    @Test
    void fetchReturnsEmptyContentWhenNoReferenceFits() {
        RagSlotSource source = new RagSlotSource((tenantId, applicationId, userId, profileId, query, topK) -> List.of(
                new RagSearchResultDTO(10L, 100L, "Large Doc", "This chunk is too large for a tiny rag slot.", "kb://large", 0.9)
        ));

        ContextSlotContent content = source.fetch(
                ContextSlot.of(ContextSlotKind.RAG_RECALL, 1),
                command("tiny budget")
        );

        assertThat(content.content()).isEmpty();
        assertThat(content.usedTokens()).isZero();
        assertThat(content.truncated()).isTrue();
    }

    @Test
    void fetchDegradesToEmptyContentWhenRagSearchFails() {
        TraceService traceService = mock(TraceService.class);
        when(traceService.startSpan(any(StartTraceSpanCommand.class)))
                .thenAnswer(invocation -> {
                    StartTraceSpanCommand start = invocation.getArgument(0);
                    return new TraceSpanDTO(77001L, start.traceId(), start.parentSpanId(), start.spanName(),
                            start.spanType(), start.component(), "RUNNING", LocalDateTime.now(), null, null,
                            null, null, start.attributes(), LocalDateTime.now());
                });
        RagSlotSource source = new RagSlotSource((tenantId, applicationId, userId, profileId, query, topK) -> {
            throw new IllegalStateException("rag down");
        }, traceService);

        ContextSlotContent content = source.fetch(
                ContextSlot.of(ContextSlotKind.RAG_RECALL, 100),
                tracedCommand("query")
        );

        assertThat(content.kind()).isEqualTo(ContextSlotKind.RAG_RECALL);
        assertThat(content.content()).isEmpty();
        assertThat(content.usedTokens()).isZero();
        assertThat(content.truncated()).isFalse();
        ArgumentCaptor<FinishTraceSpanCommand> finishCaptor = ArgumentCaptor.forClass(FinishTraceSpanCommand.class);
        verify(traceService).finishSpan(finishCaptor.capture());
        assertThat(finishCaptor.getValue().status()).isEqualTo("FAILED");
        assertThat(finishCaptor.getValue().errorCode()).isEqualTo("RAG_SEARCH_FAILED");
    }

    @Test
    void fetchPassesRagSearchSpanAsParentForInternalRagTrace() {
        TraceService traceService = mock(TraceService.class);
        when(traceService.startSpan(any(StartTraceSpanCommand.class)))
                .thenAnswer(invocation -> {
                    StartTraceSpanCommand start = invocation.getArgument(0);
                    return new TraceSpanDTO(77001L, start.traceId(), start.parentSpanId(), start.spanName(),
                            start.spanType(), start.component(), "RUNNING", LocalDateTime.now(), null, null,
                            null, null, start.attributes(), LocalDateTime.now());
                });
        AtomicLong receivedParentSpanId = new AtomicLong(-1L);
        RagSearchService searchService = new RagSearchService() {
            @Override
            public List<RagSearchResultDTO> search(
                    Long tenantId,
                    Long applicationId,
                    Long userId,
                    Long profileId,
                    String query,
                    int topK
            ) {
                throw new AssertionError("trace-aware search overload should be used");
            }

            @Override
            public List<RagSearchResultDTO> search(
                    Long tenantId,
                    Long applicationId,
                    Long userId,
                    Long profileId,
                    String query,
                    int topK,
                    String traceId,
                    Long parentSpanId
            ) {
                receivedParentSpanId.set(parentSpanId == null ? -1L : parentSpanId);
                return List.of(new RagSearchResultDTO(10L, 100L, "Basketball",
                        "Low rain supports outdoor basketball.", "", 0.91));
            }
        };
        RagSlotSource source = new RagSlotSource(searchService, traceService);

        ContextSlotContent content = source.fetch(
                ContextSlot.of(ContextSlotKind.RAG_RECALL, 100),
                tracedCommand("is it suitable to play basketball today")
        );

        assertThat(content.content()).contains("Basketball");
        assertThat(receivedParentSpanId.get()).isEqualTo(77001L);
        ArgumentCaptor<StartTraceSpanCommand> startCaptor = ArgumentCaptor.forClass(StartTraceSpanCommand.class);
        verify(traceService).startSpan(startCaptor.capture());
        assertThat(startCaptor.getValue().spanName()).isEqualTo("rag.search");
        assertThat(startCaptor.getValue().parentSpanId()).isEqualTo(42L);
        verify(traceService).finishSpan(any(FinishTraceSpanCommand.class));
    }

    private BuildAgentContextCommand command(String query) {
        return new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                query,
                1_000,
                null,
                null
        );
    }

    private BuildAgentContextCommand tracedCommand(String query) {
        return new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                query,
                1_000,
                null,
                null,
                "trace-1",
                42L
        );
    }
}
