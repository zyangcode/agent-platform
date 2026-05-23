package com.ls.agent.core.model.provider;

public final class UsageParser {

    private UsageParser() {
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (text.length() + 3) / 4);
    }
}
