package com.ls.agent.core.evaluation.dto;

import java.util.List;

public record RetrievalEvaluationReport(
        int topK,
        RetrievalEvaluationResult result,
        List<String> missingPredictionCaseIds
) {
    public RetrievalEvaluationReport {
        missingPredictionCaseIds = missingPredictionCaseIds == null
                ? List.of()
                : List.copyOf(missingPredictionCaseIds);
    }
}
