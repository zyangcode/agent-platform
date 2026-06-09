package com.ls.agent.core.evaluation.dto;

import java.util.List;

public record RetrievalPrediction(
        String caseId,
        List<String> returnedIds
) {
    public RetrievalPrediction {
        returnedIds = returnedIds == null ? List.of() : List.copyOf(returnedIds);
    }
}
