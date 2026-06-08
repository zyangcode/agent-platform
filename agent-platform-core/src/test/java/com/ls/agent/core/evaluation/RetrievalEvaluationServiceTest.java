package com.ls.agent.core.evaluation;

import com.ls.agent.core.evaluation.application.RetrievalEvaluationService;
import com.ls.agent.core.evaluation.dto.RetrievalEvaluationCase;
import com.ls.agent.core.evaluation.dto.RetrievalEvaluationResult;
import com.ls.agent.core.evaluation.dto.RetrievalPrediction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalEvaluationServiceTest {

    private final RetrievalEvaluationService service = new RetrievalEvaluationService();

    @Test
    void evaluateComputesHitRateAndMrrAtK() {
        List<RetrievalEvaluationCase> cases = List.of(
                testCase("memory-style", "memory", "answer style", List.of("mem-2")),
                testCase("rag-policy", "rag", "refund policy", List.of("chunk-7"))
        );
        Map<String, RetrievalPrediction> predictions = Map.of(
                "memory-style", prediction("memory-style", List.of("mem-1", "mem-2", "mem-3")),
                "rag-policy", prediction("rag-policy", List.of("chunk-7", "chunk-8"))
        );

        RetrievalEvaluationResult result = service.evaluate(cases, predictions, 5);

        assertThat(result.totalCases()).isEqualTo(2);
        assertThat(result.hitCount()).isEqualTo(2);
        assertThat(result.hitRate()).isEqualTo(1.0);
        assertThat(result.meanReciprocalRank()).isEqualTo(0.75);
        assertThat(result.misses()).isEmpty();
    }

    @Test
    void evaluateTreatsResultOutsideKAsMiss() {
        List<RetrievalEvaluationCase> cases = List.of(
                testCase("memory-late", "memory", "late match", List.of("mem-6"))
        );
        Map<String, RetrievalPrediction> predictions = Map.of(
                "memory-late", prediction("memory-late", List.of("mem-1", "mem-2", "mem-3", "mem-4", "mem-5", "mem-6"))
        );

        RetrievalEvaluationResult result = service.evaluate(cases, predictions, 5);

        assertThat(result.hitCount()).isZero();
        assertThat(result.hitRate()).isZero();
        assertThat(result.meanReciprocalRank()).isZero();
        assertThat(result.misses()).hasSize(1);
        assertThat(result.misses().get(0).caseId()).isEqualTo("memory-late");
        assertThat(result.misses().get(0).expectedIds()).containsExactly("mem-6");
        assertThat(result.misses().get(0).returnedIdsWithinK()).containsExactly("mem-1", "mem-2", "mem-3", "mem-4", "mem-5");
    }

    @Test
    void evaluateUsesBestRankWhenCaseHasMultipleExpectedIds() {
        List<RetrievalEvaluationCase> cases = List.of(
                testCase("rag-multi", "rag", "multiple valid chunks", List.of("chunk-9", "chunk-3"))
        );
        Map<String, RetrievalPrediction> predictions = Map.of(
                "rag-multi", prediction("rag-multi", List.of("chunk-1", "chunk-3", "chunk-9"))
        );

        RetrievalEvaluationResult result = service.evaluate(cases, predictions, 5);

        assertThat(result.hitCount()).isEqualTo(1);
        assertThat(result.meanReciprocalRank()).isEqualTo(0.5);
    }

    @Test
    void evaluateRecordsMissingPredictionAsMiss() {
        List<RetrievalEvaluationCase> cases = List.of(
                testCase("missing", "memory", "no prediction", List.of("mem-1"))
        );

        RetrievalEvaluationResult result = service.evaluate(cases, Map.of(), 5);

        assertThat(result.hitCount()).isZero();
        assertThat(result.misses()).hasSize(1);
        assertThat(result.misses().get(0).returnedIdsWithinK()).isEmpty();
    }

    @Test
    void evaluateRejectsInvalidInputs() {
        assertThatThrownBy(() -> service.evaluate(List.of(), Map.of(), 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cases");
        assertThatThrownBy(() -> service.evaluate(List.of(
                testCase("case-1", "memory", "query", List.of("mem-1"))
        ), Map.of(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK");
    }

    private RetrievalEvaluationCase testCase(String id, String sourceType, String query, List<String> expectedIds) {
        return new RetrievalEvaluationCase(id, sourceType, query, expectedIds);
    }

    private RetrievalPrediction prediction(String caseId, List<String> returnedIds) {
        return new RetrievalPrediction(caseId, returnedIds);
    }
}
