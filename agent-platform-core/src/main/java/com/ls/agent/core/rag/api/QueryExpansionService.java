package com.ls.agent.core.rag.api;

import java.util.List;

@FunctionalInterface
public interface QueryExpansionService {

    List<String> expand(String query, int maxQueries);

    static QueryExpansionService noop() {
        return (query, maxQueries) -> List.of();
    }
}
