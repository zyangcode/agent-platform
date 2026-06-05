package com.ls.agent.core.memory.application;

import com.ls.agent.core.memory.command.RecordMemoryCommand;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class PreferenceExtractor {

    private static final String MEMORY_TYPE = "PREFERENCE";
    private static final String MEMORY_CATEGORY = "preference";
    private static final String SLOT_HINT = "preference";
    private static final double IMPORTANCE = 0.8;
    private static final Pattern CLAUSE_SPLITTER = Pattern.compile("[,，。；;、]|\\s+和\\s*|和");

    public List<RecordMemoryCommand> extract(
            Long tenantId,
            Long userId,
            Long applicationId,
            Long profileId,
            Long sourceConversationId,
            String userMessage
    ) {
        if (userMessage == null || userMessage.isBlank()) {
            return List.of();
        }
        String text = userMessage.strip();
        String lowerText = text.toLowerCase(Locale.ROOT);
        if (text.contains("我不喜欢")) {
            return extractAfterSignal(
                    tenantId,
                    userId,
                    applicationId,
                    profileId,
                    sourceConversationId,
                    text,
                    "我不喜欢",
                    "用户不偏好：",
                    "negative"
            );
        }
        if (lowerText.contains("i don't like") || lowerText.contains("i dislike")) {
            String signal = firstPresentIgnoreCase(text, List.of("i don't like", "i dislike"));
            return extractAfterSignal(
                    tenantId,
                    userId,
                    applicationId,
                    profileId,
                    sourceConversationId,
                    text,
                    signal,
                    "User dispreference: ",
                    "negative"
            );
        }
        if (text.contains("我喜欢") || text.contains("我偏好") || text.contains("我希望")) {
            String signal = firstPresent(text, List.of("我喜欢", "我偏好", "我希望"));
            return extractAfterSignal(
                    tenantId,
                    userId,
                    applicationId,
                    profileId,
                    sourceConversationId,
                    text,
                    signal,
                    "用户偏好：",
                    "positive"
            );
        }
        if (lowerText.contains("i prefer") || lowerText.contains("i like") || lowerText.contains("i want")) {
            String signal = firstPresentIgnoreCase(text, List.of("i prefer", "i like", "i want"));
            return extractAfterSignal(
                    tenantId,
                    userId,
                    applicationId,
                    profileId,
                    sourceConversationId,
                    text,
                    signal,
                    "User preference: ",
                    "positive"
            );
        }
        return List.of();
    }

    private List<RecordMemoryCommand> extractAfterSignal(
            Long tenantId,
            Long userId,
            Long applicationId,
            Long profileId,
            Long sourceConversationId,
            String text,
            String signal,
            String contentPrefix,
            String polarityTag
    ) {
        int index = indexOfIgnoreCase(text, signal);
        if (index < 0) {
            return List.of();
        }
        String value = text.substring(index + signal.length()).strip();
        if (value.isBlank()) {
            return List.of();
        }
        List<RecordMemoryCommand> commands = new ArrayList<>();
        for (String clause : CLAUSE_SPLITTER.split(value)) {
            String normalized = normalizeClause(clause);
            if (normalized.isBlank()) {
                continue;
            }
            commands.add(new RecordMemoryCommand(
                    tenantId,
                    userId,
                    applicationId,
                    profileId,
                    MEMORY_TYPE,
                    contentPrefix + normalized,
                    sourceConversationId,
                    MEMORY_CATEGORY,
                    List.of(MEMORY_CATEGORY, polarityTag),
                    IMPORTANCE,
                    SLOT_HINT
            ));
        }
        return commands;
    }

    private String firstPresent(String text, List<String> signals) {
        return signals.stream()
                .filter(text::contains)
                .findFirst()
                .orElse(signals.get(0));
    }

    private String firstPresentIgnoreCase(String text, List<String> signals) {
        return signals.stream()
                .filter(signal -> indexOfIgnoreCase(text, signal) >= 0)
                .findFirst()
                .orElse(signals.get(0));
    }

    private int indexOfIgnoreCase(String text, String signal) {
        return text.toLowerCase(Locale.ROOT).indexOf(signal.toLowerCase(Locale.ROOT));
    }

    private String normalizeClause(String clause) {
        if (clause == null) {
            return "";
        }
        return clause.strip()
                .replaceFirst("^(我)?(喜欢|偏好|希望)", "")
                .replaceFirst("[.。!！?？]+$", "")
                .strip();
    }
}
