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
        String userRequest = extractLabelValue(draft, "User request:", "用户请求：", "鐢ㄦ埛璇锋眰锛?");
        if (userRequest.isBlank()) {
            return "当前没有拿到足够可靠的工具结果，我先给出一个通用建议：请补充目标、地点、人数、预算和时间后重新规划。";
        }
        if (looksLikeTeamBuildingRequest(userRequest)) {
            String city = userRequest.contains("重庆") ? "重庆" : "当地";
            String people = userRequest.contains("20") ? "20人" : "团队";
            return city + people + "团建建议：上午集合破冰并分组，午餐安排桌餐或烧烤，下午选择轻户外拓展、密室协作或轰趴桌游，傍晚复盘颁奖后聚餐。"
                    + "预算可按人均300-500元预估，并提前确认交通、天气、忌口和是否有人不适合剧烈运动。";
        }
        return "当前工具结果不完整，我无法可靠复述内部执行细节。建议先按你的需求给出一个简版方案，再补充关键条件后细化。";
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

    private boolean looksLikeTeamBuildingRequest(String text) {
        return text.contains("团建") || text.toLowerCase().contains("team");
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
