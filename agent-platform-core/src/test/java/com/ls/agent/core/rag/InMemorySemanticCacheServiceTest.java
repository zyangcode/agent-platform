package com.ls.agent.core.rag;

import com.ls.agent.core.rag.application.InMemorySemanticCacheService;
import com.ls.agent.core.rag.application.SemanticCacheProperties;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;
import com.ls.agent.core.rag.dto.SemanticCacheLookupCommand;
import com.ls.agent.core.rag.dto.SemanticCachePutCommand;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySemanticCacheServiceTest {

    @Test
    void lookupReturnsMostSimilarResultOnlyInsideSameScopeAndThreshold() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-09T00:00:00Z"));
        InMemorySemanticCacheService cache = new InMemorySemanticCacheService(
                new SemanticCacheProperties(true, "memory", 0.8, 60_000, 100),
                clock
        );
        cache.put(new SemanticCachePutCommand(
                1L,
                20001L,
                10001L,
                50001L,
                "context timeout",
                new EmbeddingVectorDTO("mock", new float[]{1.0f, 0.0f}),
                5,
                List.of(result(91001L, "context retrieval timeout"))
        ));
        cache.put(new SemanticCachePutCommand(
                1L,
                20001L,
                10001L,
                60001L,
                "context timeout other profile",
                new EmbeddingVectorDTO("mock", new float[]{1.0f, 0.0f}),
                5,
                List.of(result(91002L, "wrong profile"))
        ));

        Optional<List<RagSearchResultDTO>> hit = cache.lookup(new SemanticCacheLookupCommand(
                1L,
                20001L,
                10001L,
                50001L,
                "上下文超时",
                new EmbeddingVectorDTO("mock", new float[]{0.9f, 0.1f}),
                3
        ));
        Optional<List<RagSearchResultDTO>> miss = cache.lookup(new SemanticCacheLookupCommand(
                1L,
                20001L,
                10001L,
                50001L,
                "unrelated",
                new EmbeddingVectorDTO("mock", new float[]{0.0f, 1.0f}),
                3
        ));

        assertThat(hit).isPresent();
        assertThat(hit.orElseThrow()).extracting(RagSearchResultDTO::chunkId)
                .containsExactly(91001L);
        assertThat(miss).isEmpty();
    }

    @Test
    void lookupHonorsTtlAndTopK() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-09T00:00:00Z"));
        InMemorySemanticCacheService cache = new InMemorySemanticCacheService(
                new SemanticCacheProperties(true, "memory", 0.8, 1_000, 100),
                clock
        );
        cache.put(new SemanticCachePutCommand(
                1L,
                20001L,
                10001L,
                50001L,
                "context timeout",
                new EmbeddingVectorDTO("mock", new float[]{1.0f, 0.0f}),
                5,
                List.of(
                        result(91001L, "first"),
                        result(91002L, "second")
                )
        ));

        Optional<List<RagSearchResultDTO>> limited = cache.lookup(new SemanticCacheLookupCommand(
                1L,
                20001L,
                10001L,
                50001L,
                "context timeout",
                new EmbeddingVectorDTO("mock", new float[]{1.0f, 0.0f}),
                1
        ));
        clock.advance(Duration.ofMillis(1_001));
        Optional<List<RagSearchResultDTO>> expired = cache.lookup(new SemanticCacheLookupCommand(
                1L,
                20001L,
                10001L,
                50001L,
                "context timeout",
                new EmbeddingVectorDTO("mock", new float[]{1.0f, 0.0f}),
                1
        ));

        assertThat(limited).isPresent();
        assertThat(limited.orElseThrow()).extracting(RagSearchResultDTO::chunkId)
                .containsExactly(91001L);
        assertThat(expired).isEmpty();
    }

    @Test
    void disabledCacheDoesNotStoreOrLookup() {
        InMemorySemanticCacheService cache = new InMemorySemanticCacheService(
                new SemanticCacheProperties(false, "memory", 0.8, 60_000, 100),
                Clock.systemUTC()
        );

        cache.put(new SemanticCachePutCommand(
                1L,
                20001L,
                10001L,
                50001L,
                "context timeout",
                new EmbeddingVectorDTO("mock", new float[]{1.0f}),
                5,
                List.of(result(91001L, "context"))
        ));

        assertThat(cache.enabled()).isFalse();
        assertThat(cache.lookup(new SemanticCacheLookupCommand(
                1L,
                20001L,
                10001L,
                50001L,
                "context timeout",
                new EmbeddingVectorDTO("mock", new float[]{1.0f}),
                5
        ))).isEmpty();
    }

    private RagSearchResultDTO result(Long chunkId, String content) {
        return new RagSearchResultDTO(90000L + chunkId, chunkId, "Cached", content, "kb://" + chunkId, 0.9);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
