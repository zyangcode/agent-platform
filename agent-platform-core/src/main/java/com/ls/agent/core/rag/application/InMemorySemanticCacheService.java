package com.ls.agent.core.rag.application;

import com.ls.agent.core.rag.api.SemanticCacheService;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;
import com.ls.agent.core.rag.dto.SemanticCacheLookupCommand;
import com.ls.agent.core.rag.dto.SemanticCachePutCommand;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemorySemanticCacheService implements SemanticCacheService {

    private final SemanticCacheProperties properties;
    private final Clock clock;
    private final CopyOnWriteArrayList<Entry> entries = new CopyOnWriteArrayList<>();

    public InMemorySemanticCacheService(SemanticCacheProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public InMemorySemanticCacheService(SemanticCacheProperties properties, Clock clock) {
        this.properties = properties == null
                ? new SemanticCacheProperties(false, "noop", 0.9, 60_000L, 256)
                : properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public boolean enabled() {
        return properties.enabled();
    }

    @Override
    public Optional<List<RagSearchResultDTO>> lookup(SemanticCacheLookupCommand command) {
        if (!enabled() || invalidLookup(command)) {
            return Optional.empty();
        }
        long now = clock.millis();
        purgeExpired(now);
        return entries.stream()
                .filter(entry -> matchesScope(entry, command))
                .map(entry -> new ScoredEntry(entry, cosine(entry.queryVector(), command.queryVector())))
                .filter(scored -> scored.score() >= properties.similarityThreshold())
                .max(Comparator.comparingDouble(ScoredEntry::score)
                        .thenComparing(scored -> scored.entry().createdAtMs()))
                .map(scored -> scored.entry().results().stream()
                        .limit(Math.max(1, command.topK()))
                        .toList());
    }

    @Override
    public void put(SemanticCachePutCommand command) {
        if (!enabled() || invalidPut(command)) {
            return;
        }
        long now = clock.millis();
        purgeExpired(now);
        Entry entry = new Entry(
                command.tenantId(),
                command.applicationId(),
                command.ownerUserId(),
                command.profileId(),
                command.query(),
                command.queryVector(),
                List.copyOf(command.results()),
                now
        );
        entries.add(entry);
        trimToMaxEntries();
    }

    private boolean invalidLookup(SemanticCacheLookupCommand command) {
        return command == null
                || command.query().isBlank()
                || command.topK() <= 0
                || isEmpty(command.queryVector());
    }

    private boolean invalidPut(SemanticCachePutCommand command) {
        return command == null
                || command.query().isBlank()
                || command.results().isEmpty()
                || isEmpty(command.queryVector());
    }

    private void purgeExpired(long now) {
        entries.removeIf(entry -> now - entry.createdAtMs() > properties.ttlMs());
    }

    private void trimToMaxEntries() {
        int overflow = entries.size() - properties.maxEntries();
        if (overflow <= 0) {
            return;
        }
        List<Entry> oldest = entries.stream()
                .sorted(Comparator.comparingLong(Entry::createdAtMs))
                .limit(overflow)
                .toList();
        entries.removeAll(oldest);
    }

    private boolean matchesScope(Entry entry, SemanticCacheLookupCommand command) {
        return Objects.equals(entry.tenantId(), command.tenantId())
                && Objects.equals(entry.applicationId(), command.applicationId())
                && Objects.equals(entry.ownerUserId(), command.ownerUserId())
                && Objects.equals(entry.profileId(), command.profileId());
    }

    private boolean isEmpty(EmbeddingVectorDTO vector) {
        return vector == null || vector.dimension() == 0;
    }

    private double cosine(EmbeddingVectorDTO left, EmbeddingVectorDTO right) {
        if (left == null || right == null) {
            return 0.0;
        }
        float[] leftValues = left.values();
        float[] rightValues = right.values();
        int length = Math.min(leftValues.length, rightValues.length);
        if (length == 0) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < length; i++) {
            dot += leftValues[i] * rightValues[i];
            leftNorm += leftValues[i] * leftValues[i];
            rightNorm += rightValues[i] * rightValues[i];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private record Entry(
            Long tenantId,
            Long applicationId,
            Long ownerUserId,
            Long profileId,
            String query,
            EmbeddingVectorDTO queryVector,
            List<RagSearchResultDTO> results,
            long createdAtMs
    ) {
        private Entry {
            query = query == null ? "" : query.strip();
            queryVector = queryVector == null ? new EmbeddingVectorDTO("", new float[0]) : queryVector;
            results = results == null ? List.of() : new ArrayList<>(results);
        }
    }

    private record ScoredEntry(
            Entry entry,
            double score
    ) {
    }
}
