package com.ls.agent.core.context.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.core.context.api.ContextSlotSource;
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

import java.util.List;

public class RagSlotSource implements ContextSlotSource {

    private static final int DEFAULT_TOP_K = 5;

    private final RagSearchService ragSearchService;
    private final TraceService traceService;
    private List<RagSearchResultDTO> lastResults = List.of();

    public RagSlotSource(RagSearchService ragSearchService) {
        this(ragSearchService, null);
    }

    public RagSlotSource(RagSearchService ragSearchService, TraceService traceService) {
        this.ragSearchService = ragSearchService;
        this.traceService = traceService;
    }

    @Override
    public boolean supports(ContextSlotKind kind) {
        return ContextSlotKind.RAG_RECALL.equals(kind);
    }

    @Override
    public ContextSlotContent fetch(ContextSlot slot, BuildAgentContextCommand command) {
        if (!supports(slot.kind()) || ragSearchService == null || command == null) {
            return ContextSlotContent.empty(slot.kind());
        }
        TraceSpanDTO span = safeStartSpan(command, slot.tokenBudget());
        try {
            List<RagSearchResultDTO> results;
            if (command.traceId() == null || command.traceId().isBlank()) {
                results = ragSearchService.search(
                        command.tenantId(),
                        command.applicationId(),
                        command.userId(),
                        command.profileId(),
                        command.userInput(),
                        DEFAULT_TOP_K
                );
            } else {
                results = ragSearchService.search(
                        command.tenantId(),
                        command.applicationId(),
                        command.userId(),
                        command.profileId(),
                        command.userInput(),
                        DEFAULT_TOP_K,
                        command.traceId(),
                        span == null ? command.parentSpanId() : span.id()
                );
            }
            List<RagSearchResultDTO> safeResults = results == null ? List.of() : results;
            this.lastResults = safeResults;
            ContextSlotContent content = format(safeResults, slot.tokenBudget());
            if (span != null && span.attributes() instanceof ObjectNode attributes) {
                attributes.put("returnedCount", results == null ? 0 : results.size());
                attributes.put("usedTokens", content.usedTokens());
                attributes.put("truncated", content.truncated());
            }
            safeFinishSpan(span, "SUCCESS", null, null);
            return content;
        } catch (Exception ex) {
            safeFinishSpan(span, "FAILED", "RAG_SEARCH_FAILED", ex.getMessage());
            this.lastResults = List.of();
            return ContextSlotContent.empty(slot.kind());
        }
    }

    private ContextSlotContent format(List<RagSearchResultDTO> results, int tokenBudget) {
        if (results.isEmpty()) {
            return ContextSlotContent.empty(ContextSlotKind.RAG_RECALL);
        }
        StringBuilder builder = new StringBuilder("RAG references:\n");
        int used = 0;
        boolean truncated = false;
        for (RagSearchResultDTO result : results) {
            String line = "- " + title(result) + ": " + result.content().strip() + source(result) + "\n";
            int tokens = estimateTokens(line);
            if (used + tokens > tokenBudget) {
                truncated = true;
                break;
            }
            builder.append(line);
            used += tokens;
        }
        if (used == 0) {
            return new ContextSlotContent(ContextSlotKind.RAG_RECALL, "", 0, truncated);
        }
        return new ContextSlotContent(ContextSlotKind.RAG_RECALL, builder.toString(), used, truncated);
    }

    private String title(RagSearchResultDTO result) {
        if (!result.title().isBlank()) {
            return "[" + result.title().strip() + "]";
        }
        if (result.documentId() != null || result.chunkId() != null) {
            return "[doc=" + result.documentId() + ", chunk=" + result.chunkId() + "]";
        }
        return "[reference]";
    }

    private String source(RagSearchResultDTO result) {
        return result.sourceUri().isBlank() ? "" : " (" + result.sourceUri().strip() + ")";
    }

    public List<RagSearchResultDTO> getLastResults() {
        return lastResults;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private TraceSpanDTO safeStartSpan(BuildAgentContextCommand command, int tokenBudget) {
        if (traceService == null || command.traceId() == null || command.traceId().isBlank()) {
            return null;
        }
        try {
            ObjectNode attributes = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                    .put("topK", DEFAULT_TOP_K)
                    .put("tokenBudget", Math.max(0, tokenBudget))
                    .put("searchService", ragSearchService.getClass().getSimpleName())
                    .put("traceAware", true);
            return traceService.startSpan(new StartTraceSpanCommand(
                    command.traceId(),
                    command.parentSpanId(),
                    "rag.search",
                    "CONTEXT",
                    "core",
                    attributes
            ));
        } catch (Exception ex) {
            return null;
        }
    }

    private void safeFinishSpan(TraceSpanDTO span, String status, String errorCode, String errorMessage) {
        if (traceService == null || span == null || span.id() == null) {
            return;
        }
        try {
            traceService.finishSpan(new FinishTraceSpanCommand(span.id(), status, errorCode, errorMessage, span.attributes()));
        } catch (Exception ex) {
            // Trace is diagnostic data; it must not break context assembly.
        }
    }
}
