package com.ls.agent.core.evaluation.dto;

import java.util.List;

public record RetrievalEvaluationResult(
        int totalCases,
        int answerableCaseCount,
        int hitCount,
        double hitRate,
        double meanReciprocalRank,
        double recallAtK,
        int noAnswerCaseCount,
        int noAnswerCorrectCount,
        double noAnswerPrecision,
        List<RetrievalEvaluationMiss> misses
) {
    public RetrievalEvaluationResult {
        misses = misses == null ? List.of() : List.copyOf(misses);
    }
}
