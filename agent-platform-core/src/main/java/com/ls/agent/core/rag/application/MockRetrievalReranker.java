package com.ls.agent.core.rag.application;

import com.ls.agent.core.rag.api.RetrievalReranker;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MockRetrievalReranker implements RetrievalReranker {

    @Override
    public List<RagSearchResultDTO> rerank(String query, List<RagSearchResultDTO> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        Set<String> terms = terms(query);
        return candidates.stream()
                .sorted(Comparator
                        .<RagSearchResultDTO>comparingDouble(result -> mockScore(result, terms))
                        .reversed()
                        .thenComparing(RagSearchResultDTO::score, Comparator.reverseOrder()))
                .limit(Math.max(1, topK))
                .toList();
    }

    private double mockScore(RagSearchResultDTO result, Set<String> terms) {
        if (result == null) {
            return 0.0;
        }
        String text = (safe(result.title()) + " " + safe(result.content())).toLowerCase(Locale.ROOT);
        long hits = terms.stream().filter(text::contains).count();
        return hits + result.score() * 0.01;
    }

    private Set<String> terms(String query) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(query.toLowerCase(Locale.ROOT)
                        .replaceAll("[^\\p{IsHan}\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                        .strip()
                        .split("\\s+"))
                .filter(term -> term.length() >= 2)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
