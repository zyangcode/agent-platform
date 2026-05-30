package com.ls.agent.core.team.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.team.dto.ReviewResultDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class TeamFinalAnswerBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String build(String answerDraft, ReviewResultDTO review) {
        String draft = visibleAnswer(answerDraft);
        return draft;
    }

    private String visibleAnswer(String answerDraft) {
        String draft = answerDraft == null ? "" : answerDraft.strip();
        if (draft.isBlank()) {
            return "";
        }
        if (!containsInternalLabels(draft)) {
            return draft;
        }
        List<TaskResultLine> results = successfulResults(draft);
        Optional<String> explicitAnswer = results.stream()
                .map(TaskResultLine::answerText)
                .filter(result -> result != null && !result.isBlank())
                .max(Comparator.comparingInt(String::length));
        if (explicitAnswer.isPresent()) {
            return explicitAnswer.get();
        }
        return results.stream()
                .filter(result -> !looksLikeJsonObject(result.rawResult()))
                .map(TaskResultLine::rawResult)
                .filter(result -> result != null && !result.isBlank())
                .reduce((first, second) -> first + "\n" + second)
                .orElseGet(() -> fallbackAnswer(draft));
    }

    private boolean containsInternalLabels(String draft) {
        return draft.contains("User request:")
                || draft.contains("Goal:")
                || draft.contains("Execution results:")
                || draft.contains("用户请求：")
                || draft.contains("目标：")
                || draft.contains("执行结果：");
    }

    private List<TaskResultLine> successfulResults(String draft) {
        List<TaskResultLine> results = new ArrayList<>();
        StringBuilder currentResult = null;
        for (String line : draft.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("- ") && trimmed.contains("[SUCCESS]:")) {
                if (currentResult != null && !currentResult.toString().isBlank()) {
                    results.add(new TaskResultLine(currentResult.toString().strip()));
                }
                currentResult = new StringBuilder(trimmed.substring(trimmed.indexOf("[SUCCESS]:") + "[SUCCESS]:".length()).strip());
                continue;
            }
            if (currentResult != null && isContinuationLine(line)) {
                if (!currentResult.isEmpty()) {
                    currentResult.append("\n");
                }
                currentResult.append(trimmed);
            }
        }
        if (currentResult != null && !currentResult.toString().isBlank()) {
            results.add(new TaskResultLine(currentResult.toString().strip()));
        }
        return results;
    }

    private boolean isContinuationLine(String line) {
        return line != null && !line.isBlank() && !line.stripLeading().startsWith("- ");
    }

    private boolean looksLikeJsonObject(String result) {
        return result != null && result.strip().startsWith("{") && result.strip().endsWith("}");
    }

    private String fallbackAnswer(String draft) {
        String userRequest = extractLabelValue(draft, "User request:", "用户请求：");
        if (userRequest.isBlank()) {
            return "";
        }
        return "";
    }

    private String extractLabelValue(String draft, String... labels) {
        String[] lines = draft == null ? new String[0] : draft.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].strip();
            for (String label : labels) {
                if (line.startsWith(label)) {
                    StringBuilder value = new StringBuilder(line.substring(label.length()).strip());
                    for (int nextIndex = index + 1; nextIndex < lines.length; nextIndex++) {
                        String next = lines[nextIndex].strip();
                        if (next.isBlank() || next.startsWith("- ") || containsInternalLabels(next)) {
                            break;
                        }
                        if (!value.isEmpty()) {
                            value.append("\n");
                        }
                        value.append(next);
                    }
                    return value.toString().strip();
                }
            }
        }
        return "";
    }

    private record TaskResultLine(String rawResult) {

        private String answerText() {
            String trimmed = rawResult == null ? "" : rawResult.strip();
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                return null;
            }
            try {
                JsonNode root = OBJECT_MAPPER.readTree(trimmed);
                JsonNode answer = root.get("answer");
                if (answer != null && answer.isTextual() && !answer.asText().isBlank()) {
                    return answer.asText().strip();
                }
            } catch (Exception ignored) {
                return null;
            }
            return null;
        }
    }
}
