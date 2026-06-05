package com.ls.agent.core.rag.application;

public record OpenAiCompatibleEmbeddingServiceProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        int dimension,
        int timeoutMs
) {

    public OpenAiCompatibleEmbeddingServiceProperties {
        baseUrl = normalizeBaseUrl(baseUrl);
        apiKey = apiKey == null ? "" : apiKey.strip();
        model = model == null || model.isBlank() ? "text-embedding-3-small" : model.strip();
        dimension = Math.max(0, dimension);
        timeoutMs = timeoutMs <= 0 ? 3000 : timeoutMs;
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null || value.isBlank() ? "https://api.openai.com/v1" : value.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
