package com.ls.agent.core.rag.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.core.rag.api.RetrievalReranker;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OpenAiCompatibleRetrievalReranker implements RetrievalReranker {

    private final OpenAiCompatibleRetrievalRerankerProperties properties;
    private final RerankerHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleRetrievalReranker(
            OpenAiCompatibleRetrievalRerankerProperties properties,
            ObjectMapper objectMapper
    ) {
        this(properties, new JdkRerankerHttpClient(properties, objectMapper), objectMapper);
    }

    public OpenAiCompatibleRetrievalReranker(
            OpenAiCompatibleRetrievalRerankerProperties properties,
            RerankerHttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<RagSearchResultDTO> rerank(String query, List<RagSearchResultDTO> candidates, int topK) {
        List<RagSearchResultDTO> fallback = fallback(candidates, topK);
        if (!usable() || query == null || query.isBlank() || fallback.isEmpty()) {
            return fallback;
        }
        try {
            JsonNode response = httpClient.post(properties.path(), requestBody(query, fallback, topK), headers());
            List<RagSearchResultDTO> reranked = parseResults(response, fallback, topK);
            return reranked.isEmpty() ? fallback : reranked;
        } catch (Exception ex) {
            restoreInterrupt(ex);
            return fallback;
        }
    }

    private ObjectNode requestBody(String query, List<RagSearchResultDTO> candidates, int topK) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.model());
        body.put("query", query);
        body.put("top_n", Math.max(1, Math.min(topK, candidates.size())));
        ArrayNode documents = body.putArray("documents");
        for (RagSearchResultDTO candidate : candidates) {
            documents.add(documentText(candidate));
        }
        return body;
    }

    private List<RagSearchResultDTO> parseResults(JsonNode response, List<RagSearchResultDTO> candidates, int topK) {
        JsonNode results = response == null ? null : response.path("results");
        if (results == null || !results.isArray()) {
            return List.of();
        }
        List<RagSearchResultDTO> reranked = new ArrayList<>();
        Set<Integer> usedIndexes = new HashSet<>();
        for (JsonNode result : results) {
            int index = result.path("index").asInt(-1);
            if (index < 0 || index >= candidates.size() || !usedIndexes.add(index)) {
                continue;
            }
            double score = result.path("relevance_score").asDouble(candidates.get(index).score());
            reranked.add(withScore(candidates.get(index), score));
            if (reranked.size() >= Math.max(1, topK)) {
                break;
            }
        }
        return reranked;
    }

    private RagSearchResultDTO withScore(RagSearchResultDTO result, double score) {
        return new RagSearchResultDTO(
                result.documentId(),
                result.chunkId(),
                result.title(),
                result.content(),
                result.sourceUri(),
                score
        );
    }

    private String documentText(RagSearchResultDTO candidate) {
        if (candidate == null) {
            return "";
        }
        return (safe(candidate.title()) + "\n" + safe(candidate.content())).strip();
    }

    private List<RagSearchResultDTO> fallback(List<RagSearchResultDTO> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        return candidates.stream()
                .limit(Math.max(1, topK))
                .toList();
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void restoreInterrupt(Exception ex) {
        if (ex instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    public interface RerankerHttpClient {

        JsonNode post(String path, JsonNode body, Map<String, String> headers) throws IOException, InterruptedException;
    }

    private static final class JdkRerankerHttpClient implements RerankerHttpClient {

        private final OpenAiCompatibleRetrievalRerankerProperties properties;
        private final ObjectMapper objectMapper;
        private final HttpClient httpClient;

        private JdkRerankerHttpClient(
                OpenAiCompatibleRetrievalRerankerProperties properties,
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
                throw new IOException("HTTP " + response.statusCode() + " from reranker provider");
            }
            String responseBody = response.body();
            if (responseBody == null || responseBody.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(responseBody);
        }
    }
}
