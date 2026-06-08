package com.ls.agent.core.evaluation.dto;

import java.util.List;

public record RetrievalEvaluationMiss(
        String caseId,
        String sourceType,
        String query,
        List<String> expectedIds,
        List<String> returnedIdsWithinK
) {
    public RetrievalEvaluationMiss {
        expectedIds = expectedIds == null ? List.of() : List.copyOf(expectedIds);
        returnedIdsWithinK = returnedIdsWithinK == null ? List.of() : List.copyOf(returnedIdsWithinK);
    }
}
