package com.ls.agent.core.rag;

import com.ls.agent.core.rag.application.DefaultLocalRagEngine;
import com.ls.agent.core.rag.application.TextSplitter;
import com.ls.agent.core.rag.command.IngestKnowledgeDocumentCommand;
import com.ls.agent.core.rag.dto.RagIngestResultDTO;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultLocalRagEngineTest {

    @Test
    void ingestSearchAndDeleteUseTenantApplicationProfileBoundary() {
        DefaultLocalRagEngine engine = new DefaultLocalRagEngine(new TextSplitter());
        RagIngestResultDTO sports = engine.ingest(new IngestKnowledgeDocumentCommand(
                1L,
                20001L,
                10001L,
                50001L,
                "Sports Manual",
                "MANUAL",
                "kb://sports",
                "Outdoor basketball is suitable when rain is low and the court is dry. Avoid noon heat.",
                40,
                5
        ));
        engine.ingest(new IngestKnowledgeDocumentCommand(
                1L,
                20001L,
                10001L,
                60001L,
                "Other Profile Manual",
                "MANUAL",
                "kb://other",
                "Outdoor basketball private profile only.",
                40,
                5
        ));

        List<RagSearchResultDTO> results = engine.search(
                1L,
                20001L,
                10001L,
                50001L,
                "is outdoor basketball suitable after rain",
                5
        );

        assertThat(sports.documentId()).isNotNull();
        assertThat(sports.chunkCount()).isPositive();
        assertThat(sports.docHash()).isNotBlank();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Sports Manual");
        assertThat(results.get(0).content()).contains("Outdoor basketball");
        assertThat(results.get(0).score()).isGreaterThan(0);

        int deleted = engine.delete(1L, 20001L, 10001L, 50001L, sports.documentId());

        assertThat(deleted).isEqualTo(1);
        assertThat(engine.search(1L, 20001L, 10001L, 50001L, "basketball", 5)).isEmpty();
    }

    @Test
    void ingestSameDocumentHashReplacesOldChunks() {
        DefaultLocalRagEngine engine = new DefaultLocalRagEngine(new TextSplitter());
        IngestKnowledgeDocumentCommand command = new IngestKnowledgeDocumentCommand(
                1L,
                20001L,
                10001L,
                50001L,
                "Policy",
                "MANUAL",
                "kb://policy",
                "Refund policy allows reply within three business days.",
                40,
                5
        );

        RagIngestResultDTO first = engine.ingest(command);
        RagIngestResultDTO second = engine.ingest(command);

        assertThat(second.documentId()).isEqualTo(first.documentId());
        assertThat(engine.search(1L, 20001L, 10001L, 50001L, "refund business days", 10))
                .hasSize(1);
    }
}
