package com.ls.agent.core.rag;

import com.ls.agent.core.rag.application.TextSplitter;
import com.ls.agent.core.rag.dto.RagTextChunkDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextSplitterTest {

    @Test
    void splitKeepsMarkdownHeadingMetadataAndChunkBudget() {
        TextSplitter splitter = new TextSplitter();
        String content = """
                # Basketball Guide

                ## Weather
                Outdoor basketball is suitable when rain is low and the court is dry.

                ## Heat Safety
                Avoid noon exercise during high heat. Prefer morning or evening.
                """;

        List<RagTextChunkDTO> chunks = splitter.split("Sports Manual", "kb://sports", content, 24, 4);

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.documentTitle()).isEqualTo("Sports Manual");
            assertThat(chunk.sourceUri()).isEqualTo("kb://sports");
            assertThat(chunk.tokenCount()).isLessThanOrEqualTo(24);
            assertThat(chunk.content()).isNotBlank();
        });
        assertThat(chunks).extracting("headingPath")
                .containsExactly("Basketball Guide > Weather", "Basketball Guide > Heat Safety");
        assertThat(chunks.get(0).content()).contains("Outdoor basketball");
        assertThat(chunks.get(1).content()).contains("Avoid noon exercise");
    }

    @Test
    void splitFallsBackToOverlappingWindowsForLongText() {
        TextSplitter splitter = new TextSplitter();
        String content = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau";

        List<RagTextChunkDTO> chunks = splitter.split("Long", "", content, 5, 2);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.tokenCount()).isLessThanOrEqualTo(5));
        assertThat(chunks.get(0).content()).contains("alpha beta gamma delta epsilon");
        assertThat(chunks.get(1).content()).contains("delta epsilon");
    }

    @Test
    void splitReturnsEmptyForBlankContent() {
        TextSplitter splitter = new TextSplitter();

        assertThat(splitter.split("Blank", "", "   ", 10, 2)).isEmpty();
    }
}
