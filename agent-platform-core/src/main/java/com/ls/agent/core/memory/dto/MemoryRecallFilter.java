package com.ls.agent.core.memory.dto;

import java.time.Duration;
import java.util.List;

public record MemoryRecallFilter(
        List<String> categories,
        List<String> requireTags,
        List<String> memoryScopes,
        Long sourceConversationId,
        Double minScore,
        Integer topK,
        Duration maxAge
) {

    public MemoryRecallFilter {
        categories = normalize(categories);
        requireTags = normalize(requireTags);
        memoryScopes = normalizeUpper(memoryScopes);
    }

    public static Builder builder() {
        return new Builder();
    }

    public int resolvedTopK(int fallbackLimit) {
        if (topK != null && topK > 0) {
            return topK;
        }
        return Math.max(1, fallbackLimit);
    }

    private static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.strip().toLowerCase())
                .distinct()
                .toList();
    }

    private static List<String> normalizeUpper(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.strip().toUpperCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
    }

    public static final class Builder {

        private List<String> categories;
        private List<String> requireTags;
        private List<String> memoryScopes;
        private Long sourceConversationId;
        private Double minScore;
        private Integer topK;
        private Duration maxAge;

        private Builder() {
        }

        public Builder categories(List<String> categories) {
            this.categories = categories;
            return this;
        }

        public Builder requireTags(List<String> requireTags) {
            this.requireTags = requireTags;
            return this;
        }

        public Builder memoryScopes(List<String> memoryScopes) {
            this.memoryScopes = memoryScopes;
            return this;
        }

        public Builder sourceConversationId(Long sourceConversationId) {
            this.sourceConversationId = sourceConversationId;
            return this;
        }

        public Builder minScore(Double minScore) {
            this.minScore = minScore;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder maxAge(Duration maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        public MemoryRecallFilter build() {
            return new MemoryRecallFilter(categories, requireTags, memoryScopes, sourceConversationId, minScore, topK, maxAge);
        }
    }
}
