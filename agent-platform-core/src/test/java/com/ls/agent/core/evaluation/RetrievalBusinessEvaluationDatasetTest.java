package com.ls.agent.core.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.evaluation.application.RetrievalEvaluationFileRunner;
import com.ls.agent.core.evaluation.dto.RetrievalEvaluationCase;
import com.ls.agent.core.evaluation.dto.RetrievalEvaluationReport;
import com.ls.agent.core.evaluation.dto.RetrievalPrediction;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalBusinessEvaluationDatasetTest {

    private static final Path DATASET_DIR = Path.of("..", "实际开发", "优化阶段", "记忆系统优化");
    private static final Path CORPUS_FILE = DATASET_DIR.resolve("retrieval-eval-corpus.business.jsonl");
    private static final Path CASES_FILE = DATASET_DIR.resolve("retrieval-eval-cases.business.jsonl");
    private static final Path PREDICTIONS_FILE = DATASET_DIR.resolve("retrieval-eval-predictions.business-baseline.jsonl");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void businessDatasetUsesKnownCorpusIdsAndPassesBaselineGate() throws Exception {
        Set<String> corpusIds = readCorpusIds();
        List<RetrievalEvaluationCase> cases = readJsonl(CASES_FILE, RetrievalEvaluationCase.class);
        List<RetrievalPrediction> predictions = readJsonl(PREDICTIONS_FILE, RetrievalPrediction.class);

        assertThat(cases).hasSizeGreaterThanOrEqualTo(12);
        assertThat(cases)
                .filteredOn(evaluationCase -> evaluationCase.expectedIds().isEmpty())
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(cases)
                .extracting(RetrievalEvaluationCase::sourceType)
                .contains("memory", "rag");

        for (RetrievalEvaluationCase evaluationCase : cases) {
            assertThat(evaluationCase.query()).as(evaluationCase.id() + " query").isNotBlank();
            assertThat(corpusIds).as(evaluationCase.id() + " expected ids")
                    .containsAll(evaluationCase.expectedIds());
        }

        assertThat(predictions)
                .extracting(RetrievalPrediction::caseId)
                .containsExactlyElementsOf(cases.stream().map(RetrievalEvaluationCase::id).toList());
        for (RetrievalPrediction prediction : predictions) {
            assertThat(corpusIds).as(prediction.caseId() + " returned ids")
                    .containsAll(prediction.returnedIds());
        }

        RetrievalEvaluationReport report = new RetrievalEvaluationFileRunner(objectMapper)
                .evaluate(CASES_FILE, PREDICTIONS_FILE, 5);

        assertThat(report.result().hitRate()).isGreaterThanOrEqualTo(0.90);
        assertThat(report.result().meanReciprocalRank()).isGreaterThanOrEqualTo(0.85);
        assertThat(report.result().recallAtK()).isGreaterThanOrEqualTo(0.90);
        assertThat(report.result().noAnswerPrecision()).isEqualTo(1.0);
        assertThat(report.missingPredictionCaseIds()).isEmpty();
        assertThat(report.result().misses()).isEmpty();
    }

    private Set<String> readCorpusIds() throws Exception {
        Set<String> ids = new HashSet<>();
        for (String line : Files.readAllLines(CORPUS_FILE, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            JsonNode node = objectMapper.readTree(trimmed);
            JsonNode idNode = node.get("id");
            assertThat(idNode).as("corpus id").isNotNull();
            ids.add(idNode.asText());
        }
        assertThat(ids).hasSizeGreaterThanOrEqualTo(12);
        return ids;
    }

    private <T> List<T> readJsonl(Path file, Class<T> type) throws Exception {
        return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(line -> readJson(line, type))
                .toList();
    }

    private <T> T readJson(String line, Class<T> type) {
        try {
            return objectMapper.readValue(line, type);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse JSONL row: " + line, exception);
        }
    }
}
