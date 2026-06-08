package com.ls.agent.core.evaluation.dto;

import java.util.List;

public record RetrievalEvaluationCase(
        String id,
        String sourceType,
        String query,
        List<String> expectedIds
) {
    public RetrievalEvaluationCase {
        expectedIds = expectedIds == null ? List.of() : List.copyOf(expectedIds);
    }
}
