package com.ls.agent.core.evaluation.dto;

import java.util.List;

public record RetrievalEvaluationResult(
        int totalCases,
        int hitCount,
        double hitRate,
        double meanReciprocalRank,
        double recallAtK,
        List<RetrievalEvaluationMiss> misses
) {
    public RetrievalEvaluationResult {
        misses = misses == null ? List.of() : List.copyOf(misses);
    }
}
