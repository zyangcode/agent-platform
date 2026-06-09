package com.ls.agent.core.rag.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.core.rag.api.HypotheticalDocumentService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OpenAiCompatibleHypotheticalDocumentService implements HypotheticalDocumentService {

    private static final String SYSTEM_PROMPT = """
            You generate short hypothetical documents for RAG retrieval.
            Return only JSON in the format {"documents":["document1","document2"]}.
            Each document must be factual-sounding retrieval text derived from the user's question, without adding unsupported answers.
            """;

    private final OpenAiCompatibleHypotheticalDocumentServiceProperties properties;
    private final HydeHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleHypotheticalDocumentService(
            OpenAiCompatibleHypotheticalDocumentServiceProperties properties,
            ObjectMapper objectMapper
    ) {
        this(properties, new JdkHydeHttpClient(properties, objectMapper), objectMapper);
    }

    public OpenAiCompatibleHypotheticalDocumentService(
            OpenAiCompatibleHypotheticalDocumentServiceProperties properties,
            HydeHttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> generate(String query, int maxDocuments) {
        if (!usable() || query == null || query.isBlank() || maxDocuments <= 0) {
            return List.of();
        }
        String normalizedQuery = query.strip();
        try {
            JsonNode response = httpClient.post(properties.path(), requestBody(normalizedQuery, maxDocuments), headers());
            return parseDocuments(response, normalizedQuery, maxDocuments);
        } catch (Exception ex) {
            restoreInterrupt(ex);
            return List.of();
        }
    }

    private ObjectNode requestBody(String query, int maxDocuments) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.model());
        body.put("temperature", properties.temperature());
        ObjectNode responseFormat = body.putObject("response_format");
        responseFormat.put("type", "json_object");
        ArrayNode messages = body.putArray("messages");
        messages.add(message("system", SYSTEM_PROMPT));
        messages.add(message("user", "Original query: " + query + "\nGenerate up to " + maxDocuments + " hypothetical retrieval documents."));
        return body;
    }

    private ObjectNode message(String role, String content) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private List<String> parseDocuments(JsonNode response, String originalQuery, int maxDocuments) throws IOException {
        String content = response == null
                ? ""
                : response.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            return List.of();
        }
        JsonNode parsed = objectMapper.readTree(content);
        JsonNode documentsNode = parsed.isArray() ? parsed : parsed.path("documents");
        if (!documentsNode.isArray()) {
            return List.of();
        }
        Set<String> deduped = new LinkedHashSet<>();
        for (JsonNode documentNode : documentsNode) {
            addIfUseful(deduped, documentNode.asText(""), originalQuery);
            if (deduped.size() >= maxDocuments) {
                break;
            }
        }
        return new ArrayList<>(deduped);
    }

    private void addIfUseful(Set<String> documents, String candidate, String originalQuery) {
        if (candidate == null) {
            return;
        }
        String value = candidate.strip();
        if (value.isBlank() || value.equals(originalQuery) || documents.contains(value)) {
            return;
        }
        documents.add(value);
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

    private void restoreInterrupt(Exception ex) {
        if (ex instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    public interface HydeHttpClient {

        JsonNode post(String path, JsonNode body, Map<String, String> headers) throws IOException, InterruptedException;
    }

    private static final class JdkHydeHttpClient implements HydeHttpClient {

        private final OpenAiCompatibleHypotheticalDocumentServiceProperties properties;
        private final ObjectMapper objectMapper;
        private final HttpClient httpClient;

        private JdkHydeHttpClient(
                OpenAiCompatibleHypotheticalDocumentServiceProperties properties,
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
                throw new IOException("HTTP " + response.statusCode() + " from HyDE provider");
            }
            String responseBody = response.body();
            if (responseBody == null || responseBody.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(responseBody);
        }
    }
}
