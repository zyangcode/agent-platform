package com.ls.agent.core.rag;

import com.ls.agent.core.rag.application.MockEmbeddingService;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockEmbeddingServiceTest {

    @Test
    void embedReturnsDeterministicNormalizedVector() {
        MockEmbeddingService service = new MockEmbeddingService();

        EmbeddingVectorDTO first = service.embed("Outdoor basketball after rain");
        EmbeddingVectorDTO second = service.embed("Outdoor basketball after rain");

        assertThat(first.model()).isEqualTo("mock-hash-embedding");
        assertThat(first.dimension()).isEqualTo(768);
        assertThat(first.values()).hasSize(768);
        assertThat(first.values()).containsExactly(second.values());
        assertThat(nonZeroCount(first.values())).isPositive();
    }

    @Test
    void embedBlankTextReturnsEmptyVector() {
        MockEmbeddingService service = new MockEmbeddingService();

        EmbeddingVectorDTO vector = service.embed(" ");

        assertThat(vector.values()).isEmpty();
        assertThat(vector.dimension()).isZero();
    }

    private long nonZeroCount(float[] values) {
        long count = 0;
        for (float value : values) {
            if (value != 0.0f) {
                count++;
            }
        }
        return count;
    }
}
