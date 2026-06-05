package com.ls.agent.core.context.application;

import com.ls.agent.core.context.api.MicroCompactService;
import com.ls.agent.core.context.dto.MicroCompactResult;
import org.springframework.stereotype.Service;

@Service
public class DefaultMicroCompactService implements MicroCompactService {

    private static final int DEFAULT_MAX_CHARS = 600;
    private static final int HEAD_CHARS = 320;
    private static final int TAIL_CHARS = 160;
    private static final String RAG_PREFIX = "[rag]";
    private static final String ERROR_PREFIX = "[tool crash]";

    @Override
    public MicroCompactResult compact(String role, String content) {
        String safeContent = content == null ? "" : content;
        if (!shouldCompact(role, safeContent)) {
            return new MicroCompactResult(safeContent, false, safeContent.length(), safeContent.length());
        }
        String compacted = "[compact.micro] originalChars=" + safeContent.length()
                + ", kept=head:" + HEAD_CHARS + "+tail:" + TAIL_CHARS
                + "\n"
                + safeContent.substring(0, Math.min(HEAD_CHARS, safeContent.length()))
                + "\n...[truncated]...\n"
                + safeContent.substring(Math.max(0, safeContent.length() - TAIL_CHARS));
        if (compacted.length() > DEFAULT_MAX_CHARS) {
            compacted = compacted.substring(0, DEFAULT_MAX_CHARS);
        }
        return new MicroCompactResult(compacted, true, safeContent.length(), compacted.length());
    }

    private boolean shouldCompact(String role, String content) {
        if (content.length() <= DEFAULT_MAX_CHARS) {
            return false;
        }
        return "tool".equals(role)
                || content.startsWith(RAG_PREFIX)
                || content.startsWith(ERROR_PREFIX)
                || looksLikeLargeJson(content);
    }

    private boolean looksLikeLargeJson(String content) {
        String trimmed = content.stripLeading();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }
}
