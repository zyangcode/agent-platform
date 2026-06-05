package com.ls.agent.core.rag.application;

public record QdrantVectorStoreProperties(
        boolean enabled,
        String baseUrl,
        String collectionName,
        int dimension,
        String distance,
        int timeoutMs
) {

    public QdrantVectorStoreProperties {
        baseUrl = normalizeBaseUrl(baseUrl);
        collectionName = collectionName == null || collectionName.isBlank()
                ? "rag_chunks"
                : collectionName.strip();
        dimension = Math.max(0, dimension);
        distance = distance == null || distance.isBlank() ? "Cosine" : distance.strip();
        timeoutMs = timeoutMs <= 0 ? 3000 : timeoutMs;
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null || value.isBlank() ? "http://localhost:6333" : value.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
