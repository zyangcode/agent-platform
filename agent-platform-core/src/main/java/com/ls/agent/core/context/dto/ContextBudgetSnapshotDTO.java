package com.ls.agent.core.context.dto;

public record ContextBudgetSnapshotDTO(
        int maxContextTokens,
        int systemTokens,
        int profileTokens,
        int historyTokens,
        int memoryTokens,
        int toolsTokens,
        int experienceTokens,
        int ragTokens,
        int currentInputTokens,
        int apiMessagesTokens,
        int remainingTokens,
        boolean truncated
) {

    public static ContextBudgetSnapshotDTO minimal(int maxContextTokens, int apiMessagesTokens, boolean truncated) {
        return new ContextBudgetSnapshotDTO(
                maxContextTokens,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                apiMessagesTokens,
                remaining(maxContextTokens, apiMessagesTokens),
                truncated
        );
    }

    public ContextBudgetSnapshotDTO withMaxContextTokens(int maxContextTokens) {
        return new ContextBudgetSnapshotDTO(
                maxContextTokens,
                systemTokens,
                profileTokens,
                historyTokens,
                memoryTokens,
                toolsTokens,
                experienceTokens,
                ragTokens,
                currentInputTokens,
                apiMessagesTokens,
                remaining(maxContextTokens, apiMessagesTokens),
                truncated
        );
    }

    private static int remaining(int maxContextTokens, int usedTokens) {
        return Math.max(0, maxContextTokens - usedTokens);
    }
}
