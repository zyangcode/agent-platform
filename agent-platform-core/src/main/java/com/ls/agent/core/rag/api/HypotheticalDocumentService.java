package com.ls.agent.core.rag.api;

import java.util.List;

@FunctionalInterface
public interface HypotheticalDocumentService {

    List<String> generate(String query, int maxDocuments);

    static HypotheticalDocumentService noop() {
        return (query, maxDocuments) -> List.of();
    }
}
