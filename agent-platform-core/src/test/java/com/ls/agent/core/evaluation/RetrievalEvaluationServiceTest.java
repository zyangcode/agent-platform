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
                testCase("rag-multi", "rag", "multiple valid chunks", List.of("chunk-9", "chunk-3", "chunk-5"))
        );
        Map<String, RetrievalPrediction> predictions = Map.of(
                "rag-multi", prediction("rag-multi", List.of("chunk-1", "chunk-3", "chunk-9"))
        );

        RetrievalEvaluationResult result = service.evaluate(cases, predictions, 5);

        assertThat(result.hitCount()).isEqualTo(1);
        assertThat(result.meanReciprocalRank()).isEqualTo(0.5);
        assertThat(result.recallAtK()).isEqualTo(2.0 / 3.0);
    }

    @Test
    void evaluateAveragesRecallAcrossCases() {
        List<RetrievalEvaluationCase> cases = List.of(
                testCase("rag-half", "rag", "partial chunk recall", List.of("chunk-1", "chunk-2")),
                testCase("memory-full", "memory", "full memory recall", List.of("mem-1"))
        );
        Map<String, RetrievalPrediction> predictions = Map.of(
                "rag-half", prediction("rag-half", List.of("chunk-1", "chunk-9")),
                "memory-full", prediction("memory-full", List.of("mem-1", "mem-2"))
        );

        RetrievalEvaluationResult result = service.evaluate(cases, predictions, 5);

        assertThat(result.recallAtK()).isEqualTo(0.75);
    }

    @Test
    void evaluateSeparatesNoAnswerCasesFromAnswerableMetrics() {
        List<RetrievalEvaluationCase> cases = List.of(
                testCase("rag-answerable", "rag", "known policy", List.of("chunk-1")),
                testCase("rag-no-answer-correct", "rag", "unknown policy", List.of()),
                testCase("memory-no-answer-false-positive", "memory", "forgotten preference", List.of())
        );
        Map<String, RetrievalPrediction> predictions = Map.of(
                "rag-answerable", prediction("rag-answerable", List.of("chunk-1")),
                "rag-no-answer-correct", prediction("rag-no-answer-correct", List.of()),
                "memory-no-answer-false-positive", prediction("memory-no-answer-false-positive", List.of("mem-9"))
        );

        RetrievalEvaluationResult result = service.evaluate(cases, predictions, 5);

        assertThat(result.totalCases()).isEqualTo(3);
        assertThat(result.answerableCaseCount()).isEqualTo(1);
        assertThat(result.hitRate()).isEqualTo(1.0);
        assertThat(result.meanReciprocalRank()).isEqualTo(1.0);
        assertThat(result.recallAtK()).isEqualTo(1.0);
        assertThat(result.noAnswerCaseCount()).isEqualTo(2);
        assertThat(result.noAnswerCorrectCount()).isEqualTo(1);
        assertThat(result.noAnswerPrecision()).isEqualTo(0.5);
        assertThat(result.misses()).hasSize(1);
        assertThat(result.misses().get(0).caseId()).isEqualTo("memory-no-answer-false-positive");
        assertThat(result.misses().get(0).expectedIds()).isEmpty();
        assertThat(result.misses().get(0).returnedIdsWithinK()).containsExactly("mem-9");
    }

    @Test
    void evaluateReportsZeroNoAnswerPrecisionWhenThereAreNoNoAnswerCases() {
        List<RetrievalEvaluationCase> cases = List.of(
                testCase("memory-answerable", "memory", "known preference", List.of("mem-1"))
        );
        Map<String, RetrievalPrediction> predictions = Map.of(
                "memory-answerable", prediction("memory-answerable", List.of("mem-1"))
        );

        RetrievalEvaluationResult result = service.evaluate(cases, predictions, 5);

        assertThat(result.answerableCaseCount()).isEqualTo(1);
        assertThat(result.noAnswerCaseCount()).isZero();
        assertThat(result.noAnswerCorrectCount()).isZero();
        assertThat(result.noAnswerPrecision()).isZero();
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
