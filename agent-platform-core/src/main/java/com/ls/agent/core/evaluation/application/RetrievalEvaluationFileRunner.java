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

    public static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            Arguments arguments = Arguments.parse(args);
            RetrievalEvaluationFileRunner runner = new RetrievalEvaluationFileRunner(new ObjectMapper());
            RetrievalEvaluationReport report = runner.evaluate(arguments.casesFile(), arguments.predictionsFile(), arguments.topK());
            out.print(runner.render(report));
            List<String> gateFailures = arguments.gate().failures(report.result());
            if (gateFailures.isEmpty()) {
                if (arguments.gate().enabled()) {
                    out.println("Gate: PASS");
                }
                return 0;
            }
            err.println("Evaluation gate failed");
            for (String failure : gateFailures) {
                err.println("- " + failure);
            }
            return 2;
        } catch (NumberFormatException exception) {
            err.println("Threshold and topK values must be numeric: " + exception.getMessage());
            err.println(usage());
            return 1;
        } catch (RuntimeException exception) {
            err.println(exception.getMessage());
            err.println(usage());
            return 1;
        }
    }

    private static String usage() {
        return "Usage: RetrievalEvaluationFileRunner --cases <cases.jsonl> --predictions <predictions.jsonl> "
                + "[--topK 5] [--minHitRate 0.80] [--minMrr 0.60] [--minRecall 0.70] "
                + "[--minNoAnswerPrecision 0.90]";
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

    private record Arguments(Path casesFile, Path predictionsFile, int topK, MetricGate gate) {

        private static Arguments parse(String[] args) {
            Path casesFile = null;
            Path predictionsFile = null;
            int topK = 5;
            Double minHitRate = null;
            Double minMrr = null;
            Double minRecall = null;
            Double minNoAnswerPrecision = null;
            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                switch (arg) {
                    case "--cases" -> casesFile = Path.of(nextValue(args, ++index, "--cases"));
                    case "--predictions" -> predictionsFile = Path.of(nextValue(args, ++index, "--predictions"));
                    case "--topK" -> topK = Integer.parseInt(nextValue(args, ++index, "--topK"));
                    case "--minHitRate" -> minHitRate = threshold(nextValue(args, ++index, "--minHitRate"), "--minHitRate");
                    case "--minMrr" -> minMrr = threshold(nextValue(args, ++index, "--minMrr"), "--minMrr");
                    case "--minRecall" -> minRecall = threshold(nextValue(args, ++index, "--minRecall"), "--minRecall");
                    case "--minNoAnswerPrecision" ->
                            minNoAnswerPrecision = threshold(nextValue(args, ++index, "--minNoAnswerPrecision"), "--minNoAnswerPrecision");
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            if (casesFile == null) {
                throw new IllegalArgumentException("--cases is required");
            }
            if (predictionsFile == null) {
                throw new IllegalArgumentException("--predictions is required");
            }
            return new Arguments(
                    casesFile,
                    predictionsFile,
                    topK,
                    new MetricGate(minHitRate, minMrr, minRecall, minNoAnswerPrecision)
            );
        }

        private static String nextValue(String[] args, int index, String flag) {
            if (index >= args.length) {
                throw new IllegalArgumentException(flag + " requires a value");
            }
            return args[index];
        }

        private static double threshold(String value, String flag) {
            double parsed = Double.parseDouble(value);
            if (parsed < 0.0 || parsed > 1.0) {
                throw new IllegalArgumentException(flag + " must be between 0 and 1");
            }
            return parsed;
        }
    }

    private record JsonlRow<T>(int lineNumber, T value) {
    }

    private record MetricGate(
            Double minHitRate,
            Double minMrr,
            Double minRecall,
            Double minNoAnswerPrecision
    ) {

        private boolean enabled() {
            return minHitRate != null
                    || minMrr != null
                    || minRecall != null
                    || minNoAnswerPrecision != null;
        }

        private List<String> failures(RetrievalEvaluationResult result) {
            List<String> failures = new ArrayList<>();
            addFailure(failures, "hitRate", result.hitRate(), minHitRate);
            addFailure(failures, "mrrAtK", result.meanReciprocalRank(), minMrr);
            addFailure(failures, "recallAtK", result.recallAtK(), minRecall);
            addFailure(failures, "noAnswerPrecision", result.noAnswerPrecision(), minNoAnswerPrecision);
            return failures;
        }

        private void addFailure(List<String> failures, String metricName, double actual, Double minimum) {
            if (minimum != null && actual < minimum) {
                failures.add(metricName + " " + formatStatic(actual) + " < " + formatStatic(minimum));
            }
        }

        private static String formatStatic(double value) {
            return String.format(java.util.Locale.ROOT, "%.4f", value);
        }
    }
}
