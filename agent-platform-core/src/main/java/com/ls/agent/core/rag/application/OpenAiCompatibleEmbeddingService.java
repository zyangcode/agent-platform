package com.ls.agent.core.rag.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.core.rag.api.EmbeddingService;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpenAiCompatibleEmbeddingService implements EmbeddingService {

    private final OpenAiCompatibleEmbeddingServiceProperties properties;
    private final EmbeddingHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleEmbeddingService(
            OpenAiCompatibleEmbeddingServiceProperties properties,
            ObjectMapper objectMapper
    ) {
        this(properties, new JdkEmbeddingHttpClient(properties, objectMapper), objectMapper);
    }

    public OpenAiCompatibleEmbeddingService(
            OpenAiCompatibleEmbeddingServiceProperties properties,
            EmbeddingHttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public EmbeddingVectorDTO embed(String text) {
        if (!usable() || text == null || text.isBlank()) {
            return emptyVector();
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", properties.model());
            body.put("input", text);
            body.put("encoding_format", "float");
            if (properties.dimension() > 0) {
                body.put("dimensions", properties.dimension());
            }
            JsonNode response = httpClient.post("/embeddings", body, headers());
            return parseVector(response);
        } catch (Exception ex) {
            restoreInterrupt(ex);
            return emptyVector();
        }
    }

    private EmbeddingVectorDTO parseVector(JsonNode response) {
        JsonNode embedding = response == null
                ? null
                : response.path("data").path(0).path("embedding");
        if (embedding == null || !embedding.isArray()) {
            return emptyVector();
        }
        float[] values = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            values[i] = (float) embedding.get(i).asDouble();
        }
        return new EmbeddingVectorDTO(properties.model(), values);
    }

    private Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + properties.apiKey());
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private boolean usable() {
        return properties != null && properties.enabled() && !properties.apiKey().isBlank();
    }

    private EmbeddingVectorDTO emptyVector() {
        return new EmbeddingVectorDTO(properties == null ? "" : properties.model(), new float[0]);
    }

    private void restoreInterrupt(Exception ex) {
        if (ex instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    public interface EmbeddingHttpClient {

        JsonNode post(String path, JsonNode body, Map<String, String> headers) throws IOException, InterruptedException;
    }

    private static final class JdkEmbeddingHttpClient implements EmbeddingHttpClient {

        private final OpenAiCompatibleEmbeddingServiceProperties properties;
        private final ObjectMapper objectMapper;
        private final HttpClient httpClient;

        private JdkEmbeddingHttpClient(
                OpenAiCompatibleEmbeddingServiceProperties properties,
                ObjectMapper objectMapper
        ) {
            this.properties = properties;
            this.objectMapper = objectMapper;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(properties.timeoutMs()))
                    .build();
        }

        @Override
        public JsonNode post(String path, JsonNode body, Map<String, String> headers)
                throws IOException, InterruptedException {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.baseUrl() + path))
                    .timeout(Duration.ofMillis(properties.timeoutMs()));
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
            String json = objectMapper.writeValueAsString(body == null ? objectMapper.createObjectNode() : body);
            HttpResponse<String> response = httpClient.send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode() + " from embedding provider");
            }
            String responseBody = response.body();
            if (responseBody == null || responseBody.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(responseBody);
        }
    }
}
