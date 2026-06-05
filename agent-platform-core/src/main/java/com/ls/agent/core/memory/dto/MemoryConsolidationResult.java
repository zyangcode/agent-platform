package com.ls.agent.core.memory.dto;

public record MemoryConsolidationResult(
        int scannedCount,
        int expiredCount,
        int decayedCount,
        int mergedCount
) {
}
