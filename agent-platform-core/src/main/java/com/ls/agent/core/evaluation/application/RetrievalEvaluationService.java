package com.ls.agent.core.evaluation.application;

import com.ls.agent.core.evaluation.dto.RetrievalEvaluationCase;
import com.ls.agent.core.evaluation.dto.RetrievalEvaluationMiss;
import com.ls.agent.core.evaluation.dto.RetrievalEvaluationResult;
import com.ls.agent.core.evaluation.dto.RetrievalPrediction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class RetrievalEvaluationService {

    public RetrievalEvaluationResult evaluate(
            List<RetrievalEvaluationCase> cases,
            Map<String, RetrievalPrediction> predictions,
            int topK
    ) {
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("cases must not be empty");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }

        Map<String, RetrievalPrediction> safePredictions = predictions == null ? Map.of() : predictions;
        int hitCount = 0;
        double reciprocalRankSum = 0.0;
        List<RetrievalEvaluationMiss> misses = new ArrayList<>();

        for (RetrievalEvaluationCase evaluationCase : cases) {
            List<String> expectedIds = normalizedIds(evaluationCase.expectedIds());
            List<String> returnedIdsWithinK = topKIds(safePredictions.get(evaluationCase.id()), topK);
            int firstRank = firstExpectedRank(expectedIds, returnedIdsWithinK);

            if (firstRank > 0) {
                hitCount++;
                reciprocalRankSum += 1.0 / firstRank;
            } else {
                misses.add(new RetrievalEvaluationMiss(
                        evaluationCase.id(),
                        evaluationCase.sourceType(),
                        evaluationCase.query(),
                        expectedIds,
                        returnedIdsWithinK
                ));
            }
        }

        int totalCases = cases.size();
        return new RetrievalEvaluationResult(
                totalCases,
                hitCount,
                hitCount / (double) totalCases,
                reciprocalRankSum / totalCases,
                misses
        );
    }

    private List<String> topKIds(RetrievalPrediction prediction, int topK) {
        if (prediction == null || prediction.returnedIds().isEmpty()) {
            return List.of();
        }
        return normalizedIds(prediction.returnedIds()).stream()
                .limit(topK)
                .toList();
    }

    private int firstExpectedRank(List<String> expectedIds, List<String> returnedIds) {
        Set<String> expected = new HashSet<>(expectedIds);
        for (int index = 0; index < returnedIds.size(); index++) {
            if (expected.contains(returnedIds.get(index))) {
                return index + 1;
            }
        }
        return -1;
    }

    private List<String> normalizedIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
    }
}
