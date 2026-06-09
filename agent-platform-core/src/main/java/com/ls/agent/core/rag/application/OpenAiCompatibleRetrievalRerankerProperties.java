package com.ls.agent.core.rag.application;

public record OpenAiCompatibleRetrievalRerankerProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        String path,
        int timeoutMs
) {

    public OpenAiCompatibleRetrievalRerankerProperties {
        baseUrl = normalizeBaseUrl(baseUrl);
        apiKey = apiKey == null ? "" : apiKey.strip();
        model = model == null || model.isBlank() ? "rerank-v1" : model.strip();
        path = normalizePath(path);
        timeoutMs = timeoutMs <= 0 ? 3000 : timeoutMs;
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null || value.isBlank() ? "https://api.openai.com/v1" : value.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizePath(String value) {
        String normalized = value == null || value.isBlank() ? "/rerank" : value.strip();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }
}
