package com.ls.agent.core.rag.application;

public record SemanticCacheProperties(
        boolean enabled,
        String provider,
        double similarityThreshold,
        long ttlMs,
        int maxEntries
) {

    public SemanticCacheProperties {
        provider = provider == null || provider.isBlank() ? "noop" : provider.strip().toLowerCase();
        similarityThreshold = Math.max(0.0, Math.min(1.0, similarityThreshold));
        ttlMs = ttlMs <= 0 ? 60_000L : ttlMs;
        maxEntries = maxEntries <= 0 ? 256 : maxEntries;
    }
}
