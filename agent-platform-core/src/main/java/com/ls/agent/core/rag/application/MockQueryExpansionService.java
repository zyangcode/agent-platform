package com.ls.agent.core.rag.application;

import com.ls.agent.core.rag.api.QueryExpansionService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MockQueryExpansionService implements QueryExpansionService {

    @Override
    public List<String> expand(String query, int maxQueries) {
        if (query == null || query.isBlank() || maxQueries <= 0) {
            return List.of();
        }
        String normalized = query.strip();
        Set<String> expanded = new LinkedHashSet<>();
        for (String part : normalized.split("\\s*(?:和|与|以及|并且|and|&)\\s*")) {
            addIfUseful(expanded, normalized, part);
            if (expanded.size() >= maxQueries) {
                break;
            }
        }
        return expanded.stream().limit(maxQueries).toList();
    }

    private void addIfUseful(Set<String> expanded, String original, String candidate) {
        if (candidate == null) {
            return;
        }
        String value = candidate.strip();
        if (!value.isBlank() && value.length() >= 2 && !value.equals(original)) {
            expanded.add(value);
        }
    }
}
