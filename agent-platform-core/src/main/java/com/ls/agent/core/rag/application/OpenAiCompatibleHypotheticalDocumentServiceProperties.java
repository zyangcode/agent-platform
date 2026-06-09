package com.ls.agent.core.rag.application;

public record OpenAiCompatibleHypotheticalDocumentServiceProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        double temperature,
        String path,
        int timeoutMs
) {

    public OpenAiCompatibleHypotheticalDocumentServiceProperties {
        baseUrl = normalizeBaseUrl(baseUrl);
        apiKey = apiKey == null ? "" : apiKey.strip();
        model = model == null || model.isBlank() ? "gpt-4o-mini" : model.strip();
        temperature = Math.max(0.0, Math.min(2.0, temperature));
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
        String normalized = value == null || value.isBlank() ? "/chat/completions" : value.strip();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }
}
