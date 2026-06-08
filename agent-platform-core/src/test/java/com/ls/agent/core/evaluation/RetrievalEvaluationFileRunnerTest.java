package com.ls.agent.core.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.evaluation.application.RetrievalEvaluationFileRunner;
import com.ls.agent.core.evaluation.dto.RetrievalEvaluationReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalEvaluationFileRunnerTest {

    private final RetrievalEvaluationFileRunner runner = new RetrievalEvaluationFileRunner(new ObjectMapper());

    @TempDir
    Path tempDir;

    @Test
    void evaluateReadsJsonlFilesAndReturnsReport() throws Exception {
        Path cases = tempDir.resolve("cases.jsonl");
        Path predictions = tempDir.resolve("predictions.jsonl");
        Files.writeString(cases, """
                # seed cases
                {"id":"memory-style","sourceType":"memory","query":"answer style","expectedIds":["memory-1"]}

                {"id":"rag-no-answer","sourceType":"rag","query":"unknown payment rule","expectedIds":[]}
                """, StandardCharsets.UTF_8);
        Files.writeString(predictions, """
                {"caseId":"memory-style","returnedIds":["memory-9","memory-1"]}
                {"caseId":"rag-no-answer","returnedIds":[]}
                """, StandardCharsets.UTF_8);

        RetrievalEvaluationReport report = runner.evaluate(cases, predictions, 5);

        assertThat(report.topK()).isEqualTo(5);
        assertThat(report.result().totalCases()).isEqualTo(2);
        assertThat(report.result().answerableCaseCount()).isEqualTo(1);
        assertThat(report.result().hitRate()).isEqualTo(1.0);
        assertThat(report.result().meanReciprocalRank()).isEqualTo(0.5);
        assertThat(report.result().noAnswerPrecision()).isEqualTo(1.0);
        assertThat(report.missingPredictionCaseIds()).isEmpty();
    }

    @Test
    void evaluateReportsMissingPredictions() throws Exception {
        Path cases = tempDir.resolve("cases.jsonl");
        Path predictions = tempDir.resolve("predictions.jsonl");
        Files.writeString(cases, """
                {"id":"memory-style","sourceType":"memory","query":"answer style","expectedIds":["memory-1"]}
                {"id":"rag-policy","sourceType":"rag","query":"refund policy","expectedIds":["chunk-1"]}
                """, StandardCharsets.UTF_8);
        Files.writeString(predictions, """
                {"caseId":"memory-style","returnedIds":["memory-1"]}
                """, StandardCharsets.UTF_8);

        RetrievalEvaluationReport report = runner.evaluate(cases, predictions, 5);

        assertThat(report.missingPredictionCaseIds()).containsExactly("rag-policy");
        assertThat(report.result().misses()).hasSize(1);
        assertThat(report.result().misses().get(0).caseId()).isEqualTo("rag-policy");
    }

    @Test
    void evaluateRejectsPredictionWithoutCaseId() throws Exception {
        Path cases = tempDir.resolve("cases.jsonl");
        Path predictions = tempDir.resolve("predictions.jsonl");
        Files.writeString(cases, """
                {"id":"memory-style","sourceType":"memory","query":"answer style","expectedIds":["memory-1"]}
                """, StandardCharsets.UTF_8);
        Files.writeString(predictions, """
                {"caseId":"memory-style","returnedIds":["memory-1"]}
                {"returnedIds":["memory-1"]}
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> runner.evaluate(cases, predictions, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("caseId")
                .hasMessageContaining("predictions.jsonl:2");
    }

    @Test
    void evaluateRejectsCaseWithoutId() throws Exception {
        Path cases = tempDir.resolve("cases.jsonl");
        Path predictions = tempDir.resolve("predictions.jsonl");
        Files.writeString(cases, """
                {"sourceType":"memory","query":"answer style","expectedIds":["memory-1"]}
                """, StandardCharsets.UTF_8);
        Files.writeString(predictions, """
                {"caseId":"memory-style","returnedIds":["memory-1"]}
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> runner.evaluate(cases, predictions, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id")
                .hasMessageContaining("cases.jsonl:1");
    }

    @Test
    void renderReportProducesStableSummary() throws Exception {
        Path cases = tempDir.resolve("cases.jsonl");
        Path predictions = tempDir.resolve("predictions.jsonl");
        Files.writeString(cases, """
                {"id":"memory-style","sourceType":"memory","query":"answer style","expectedIds":["memory-1"]}
                {"id":"rag-no-answer","sourceType":"rag","query":"unknown payment rule","expectedIds":[]}
                """, StandardCharsets.UTF_8);
        Files.writeString(predictions, """
                {"caseId":"memory-style","returnedIds":["memory-1"]}
                {"caseId":"rag-no-answer","returnedIds":["chunk-9"]}
                """, StandardCharsets.UTF_8);

        RetrievalEvaluationReport report = runner.evaluate(cases, predictions, 5);

        assertThat(runner.render(report))
                .contains("topK=5")
                .contains("totalCases=2")
                .contains("answerableCaseCount=1")
                .contains("hitRate=1.0000")
                .contains("noAnswerPrecision=0.0000")
                .contains("misses=1")
                .contains("rag-no-answer");
    }

    @Test
    void runReturnsTwoWhenMetricThresholdFails() throws Exception {
        Path cases = tempDir.resolve("cases.jsonl");
        Path predictions = tempDir.resolve("predictions.jsonl");
        Files.writeString(cases, """
                {"id":"memory-style","sourceType":"memory","query":"answer style","expectedIds":["memory-1"]}
                """, StandardCharsets.UTF_8);
        Files.writeString(predictions, """
                {"caseId":"memory-style","returnedIds":["memory-9"]}
                """, StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = RetrievalEvaluationFileRunner.run(new String[]{
                        "--cases", cases.toString(),
                        "--predictions", predictions.toString(),
                        "--topK", "5",
                        "--minHitRate", "0.80"
                },
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isEqualTo(2);
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("hitRate=0.0000");
        assertThat(err.toString(StandardCharsets.UTF_8))
                .contains("Evaluation gate failed")
                .contains("hitRate 0.0000 < 0.8000");
    }

    @Test
    void runReturnsZeroWhenAllMetricThresholdsPass() throws Exception {
        Path cases = tempDir.resolve("cases.jsonl");
        Path predictions = tempDir.resolve("predictions.jsonl");
        Files.writeString(cases, """
                {"id":"memory-style","sourceType":"memory","query":"answer style","expectedIds":["memory-1"]}
                {"id":"rag-no-answer","sourceType":"rag","query":"unknown payment rule","expectedIds":[]}
                """, StandardCharsets.UTF_8);
        Files.writeString(predictions, """
                {"caseId":"memory-style","returnedIds":["memory-1"]}
                {"caseId":"rag-no-answer","returnedIds":[]}
                """, StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = RetrievalEvaluationFileRunner.run(new String[]{
                        "--cases", cases.toString(),
                        "--predictions", predictions.toString(),
                        "--minHitRate", "1.0",
                        "--minMrr", "1.0",
                        "--minRecall", "1.0",
                        "--minNoAnswerPrecision", "1.0"
                },
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isZero();
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("Gate: PASS");
        assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void runRejectsThresholdOutsideZeroToOne() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = RetrievalEvaluationFileRunner.run(new String[]{
                        "--cases", "cases.jsonl",
                        "--predictions", "predictions.jsonl",
                        "--minHitRate", "1.01"
                },
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8))
                .contains("--minHitRate")
                .contains("between 0 and 1");
    }
}
