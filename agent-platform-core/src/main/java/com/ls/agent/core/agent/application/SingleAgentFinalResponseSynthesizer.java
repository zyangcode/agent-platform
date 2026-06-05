package com.ls.agent.core.agent.application;

import com.ls.agent.core.model.dto.ModelMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SingleAgentFinalResponseSynthesizer {

    private static final String FALLBACK_ANSWER = """
            I could not produce a reliable final answer from the available tool results. Please try again with more details or adjust the enabled tools.
            """.strip();

    public String cleanUserVisibleAnswer(String assistantMessage) {
        String cleaned = clean(assistantMessage);
        if (cleaned.isBlank() || stillInternal(cleaned)) {
            return FALLBACK_ANSWER;
        }
        return cleaned;
    }

    public List<ModelMessage> fallbackMessages(List<ModelMessage> accumulatedMessages) {
        List<ModelMessage> fallbackMessages = new ArrayList<>();
        for (ModelMessage msg : accumulatedMessages) {
            if ("system".equals(msg.role())) {
                fallbackMessages.add(new ModelMessage("system", stripToolListings(msg.content())));
            } else {
                fallbackMessages.add(msg);
            }
        }
        fallbackMessages.add(new ModelMessage("user",
                "You were unable to fully resolve the request using tools within the allowed steps. "
                        + "Based on all the information gathered above, please provide the best possible answer "
                        + "to the original question. Do NOT output tool-call format like @skill: or @mcp:. "
                        + "Do NOT mention traces, spans, JSON, or internal execution details. "
                        + "If you still cannot answer, honestly state what information is missing."));
        return fallbackMessages;
    }

    private String clean(String assistantMessage) {
        if (assistantMessage == null || assistantMessage.isBlank()) {
            return "";
        }
        String normalized = assistantMessage.replace("\r\n", "\n").replace('\r', '\n');
        List<String> keptLines = new ArrayList<>();
        for (String line : normalized.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (isInternalLine(trimmed)) {
                continue;
            }
            keptLines.add(trimmed);
        }
        return String.join("\n", keptLines).strip();
    }

    private String stripToolListings(String systemPrompt) {
        if (systemPrompt == null) {
            return "";
        }
        return systemPrompt
                .replaceAll("(?m)^Available skills:.*(\\n(?:- .*\\n)*)?", "")
                .replaceAll("(?m)^Available MCP tools:.*(\\n(?:- .*\\n)*)?", "")
                .strip();
    }

    private boolean isInternalLine(String line) {
        String lower = line.toLowerCase();
        return line.startsWith("@skill:")
                || line.startsWith("@mcp:")
                || line.startsWith("[tool ")
                || line.startsWith("[compact.")
                || line.startsWith("[rag]")
                || line.startsWith("[mock search]")
                || lower.startsWith("task list:")
                || lower.startsWith("tasks:")
                || lower.startsWith("message_delta")
                || lower.startsWith("trace_id")
                || lower.startsWith("traceid")
                || lower.startsWith("span_id")
                || lower.startsWith("spanid")
                || looksLikeJsonObject(line)
                || looksLikeJsonArray(line)
                || lower.contains("java.lang.")
                || lower.contains("stack trace")
                || lower.contains("trace span")
                || lower.contains("model.invoke")
                || lower.contains("traceid");
    }

    private boolean stillInternal(String text) {
        String stripped = text.strip();
        String lower = stripped.toLowerCase();
        return stripped.startsWith("@skill:")
                || stripped.startsWith("@mcp:")
                || stripped.startsWith("[tool ")
                || stripped.startsWith("[compact.")
                || stripped.startsWith("[rag]")
                || stripped.startsWith("[mock search]")
                || lower.startsWith("message_delta")
                || lower.startsWith("trace_id")
                || lower.startsWith("traceid")
                || lower.startsWith("span_id")
                || lower.startsWith("spanid")
                || looksLikeJsonObject(stripped)
                || looksLikeJsonArray(stripped)
                || lower.contains("java.lang.")
                || lower.contains("stack trace")
                || lower.contains("trace span")
                || lower.contains("model.invoke")
                || lower.contains("traceid");
    }

    private boolean looksLikeJsonObject(String text) {
        return text.startsWith("{") && text.endsWith("}");
    }

    private boolean looksLikeJsonArray(String text) {
        return text.startsWith("[") && text.endsWith("]");
    }
}
