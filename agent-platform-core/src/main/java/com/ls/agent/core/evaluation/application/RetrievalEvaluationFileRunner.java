package com.ls.agent.core.evaluation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.evaluation.dto.RetrievalEvaluationCase;
import com.ls.agent.core.evaluation.dto.RetrievalEvaluationMiss;
import com.ls.agent.core.evaluation.dto.RetrievalEvaluationReport;
import com.ls.agent.core.evaluation.dto.RetrievalEvaluationResult;
import com.ls.agent.core.evaluation.dto.RetrievalPrediction;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RetrievalEvaluationFileRunner {

    private final ObjectMapper objectMapper;
    private final RetrievalEvaluationService evaluationService;

    public RetrievalEvaluationFileRunner(ObjectMapper objectMapper) {
        this(objectMapper, new RetrievalEvaluationService());
    }

    RetrievalEvaluationFileRunner(ObjectMapper objectMapper, RetrievalEvaluationService evaluationService) {
        this.objectMapper = objectMapper;
        this.evaluationService = evaluationService;
    }

    public RetrievalEvaluationReport evaluate(Path casesFile, Path predictionsFile, int topK) {
        List<JsonlRow<RetrievalEvaluationCase>> caseRows = readJsonl(casesFile, RetrievalEvaluationCase.class);
        List<JsonlRow<RetrievalPrediction>> predictionRows = readJsonl(predictionsFile, RetrievalPrediction.class);
        List<RetrievalEvaluationCase> cases = new ArrayList<>();
        for (JsonlRow<RetrievalEvaluationCase> row : caseRows) {
            RetrievalEvaluationCase evaluationCase = row.value();
            normalizeRequired(evaluationCase.id(), "id", casesFile, row.lineNumber());
            cases.add(evaluationCase);
        }
        Map<String, RetrievalPrediction> predictionByCaseId = new LinkedHashMap<>();
        for (JsonlRow<RetrievalPrediction> row : predictionRows) {
            RetrievalPrediction prediction = row.value();
            String caseId = normalizeRequired(prediction.caseId(), "caseId", predictionsFile, row.lineNumber());
            predictionByCaseId.put(caseId, prediction);
        }

        RetrievalEvaluationResult result = evaluationService.evaluate(cases, predictionByCaseId, topK);
        List<String> missingPredictionCaseIds = cases.stream()
                .map(RetrievalEvaluationCase::id)
                .filter(caseId -> !predictionByCaseId.containsKey(caseId))
                .toList();
        return new RetrievalEvaluationReport(topK, result, missingPredictionCaseIds);
    }

    public String render(RetrievalEvaluationReport report) {
        RetrievalEvaluationResult result = report.result();
        StringBuilder builder = new StringBuilder();
        builder.append("Retrieval Evaluation Report").append(System.lineSeparator());
        builder.append("topK=").append(report.topK()).append(System.lineSeparator());
        builder.append("totalCases=").append(result.totalCases()).append(System.lineSeparator());
        builder.append("answerableCaseCount=").append(result.answerableCaseCount()).append(System.lineSeparator());
        builder.append("hitCount=").append(result.hitCount()).append(System.lineSeparator());
        builder.append("hitRate=").append(format(result.hitRate())).append(System.lineSeparator());
        builder.append("mrrAtK=").append(format(result.meanReciprocalRank())).append(System.lineSeparator());
        builder.append("recallAtK=").append(format(result.recallAtK())).append(System.lineSeparator());
        builder.append("noAnswerCaseCount=").append(result.noAnswerCaseCount()).append(System.lineSeparator());
        builder.append("noAnswerCorrectCount=").append(result.noAnswerCorrectCount()).append(System.lineSeparator());
        builder.append("noAnswerPrecision=").append(format(result.noAnswerPrecision())).append(System.lineSeparator());
        builder.append("missingPredictions=").append(report.missingPredictionCaseIds().size()).append(System.lineSeparator());
        builder.append("misses=").append(result.misses().size()).append(System.lineSeparator());

        if (!report.missingPredictionCaseIds().isEmpty()) {
            builder.append("Missing prediction case ids:").append(System.lineSeparator());
            for (String caseId : report.missingPredictionCaseIds()) {
                builder.append("- ").append(caseId).append(System.lineSeparator());
            }
        }
        if (!result.misses().isEmpty()) {
            builder.append("Miss details:").append(System.lineSeparator());
            for (RetrievalEvaluationMiss miss : result.misses()) {
                builder.append("- ")
                        .append(miss.caseId())
                        .append(" [")
                        .append(miss.sourceType())
                        .append("] expected=")
                        .append(miss.expectedIds())
                        .append(" returned=")
                        .append(miss.returnedIdsWithinK())
                        .append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            Arguments arguments = Arguments.parse(args);
            RetrievalEvaluationFileRunner runner = new RetrievalEvaluationFileRunner(new ObjectMapper());
            RetrievalEvaluationReport report = runner.evaluate(arguments.casesFile(), arguments.predictionsFile(), arguments.topK());
            out.print(runner.render(report));
            return 0;
        } catch (RuntimeException exception) {
            err.println(exception.getMessage());
            err.println("Usage: RetrievalEvaluationFileRunner --cases <cases.jsonl> --predictions <predictions.jsonl> [--topK 5]");
            return 1;
        }
    }

    private <T> List<JsonlRow<T>> readJsonl(Path file, Class<T> type) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<JsonlRow<T>> rows = new ArrayList<>();
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index).trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                rows.add(new JsonlRow<>(index + 1, readLine(file, index + 1, line, type)));
            }
            if (rows.isEmpty()) {
                throw new IllegalArgumentException(file + " must contain at least one JSONL row");
            }
            return rows;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read " + file + ": " + exception.getMessage(), exception);
        }
    }

    private <T> T readLine(Path file, int lineNumber, String line, Class<T> type) {
        try {
            return objectMapper.readValue(line, type);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to parse " + file.getFileName() + ":" + lineNumber + ": " + line, exception);
        }
    }

    private String normalizeRequired(String value, String fieldName, Path file, int lineNumber) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(file.getFileName() + ":" + lineNumber + " requires " + fieldName);
        }
        return value.trim();
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private record Arguments(Path casesFile, Path predictionsFile, int topK) {

        private static Arguments parse(String[] args) {
            Path casesFile = null;
            Path predictionsFile = null;
            int topK = 5;
            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                switch (arg) {
                    case "--cases" -> casesFile = Path.of(nextValue(args, ++index, "--cases"));
                    case "--predictions" -> predictionsFile = Path.of(nextValue(args, ++index, "--predictions"));
                    case "--topK" -> topK = Integer.parseInt(nextValue(args, ++index, "--topK"));
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            if (casesFile == null) {
                throw new IllegalArgumentException("--cases is required");
            }
            if (predictionsFile == null) {
                throw new IllegalArgumentException("--predictions is required");
            }
            return new Arguments(casesFile, predictionsFile, topK);
        }

        private static String nextValue(String[] args, int index, String flag) {
            if (index >= args.length) {
                throw new IllegalArgumentException(flag + " requires a value");
            }
            return args[index];
        }
    }

    private record JsonlRow<T>(int lineNumber, T value) {
    }
}
