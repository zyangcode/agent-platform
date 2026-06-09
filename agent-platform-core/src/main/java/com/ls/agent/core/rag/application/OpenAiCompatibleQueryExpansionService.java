package com.ls.agent.core.rag.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.core.rag.api.QueryExpansionService;

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

public class OpenAiCompatibleQueryExpansionService implements QueryExpansionService {

    private static final String SYSTEM_PROMPT = """
            你是检索查询改写器。请根据用户原始问题生成语义等价、适合 RAG 检索的查询改写。
            只输出 JSON，不要解释。格式为 {"queries":["query1","query2"]}。
            每条查询必须短、具体、保留原始意图，不能加入原问题没有的信息。
            """;

    private final OpenAiCompatibleQueryExpansionServiceProperties properties;
    private final QueryExpansionHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleQueryExpansionService(
            OpenAiCompatibleQueryExpansionServiceProperties properties,
            ObjectMapper objectMapper
    ) {
        this(properties, new JdkQueryExpansionHttpClient(properties, objectMapper), objectMapper);
    }

    public OpenAiCompatibleQueryExpansionService(
            OpenAiCompatibleQueryExpansionServiceProperties properties,
            QueryExpansionHttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> expand(String query, int maxQueries) {
        if (!usable() || query == null || query.isBlank() || maxQueries <= 0) {
            return List.of();
        }
        String normalizedQuery = query.strip();
        try {
            JsonNode response = httpClient.post(properties.path(), requestBody(normalizedQuery, maxQueries), headers());
            return parseQueries(response, normalizedQuery, maxQueries);
        } catch (Exception ex) {
            restoreInterrupt(ex);
            return List.of();
        }
    }

    private ObjectNode requestBody(String query, int maxQueries) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.model());
        body.put("temperature", properties.temperature());
        ObjectNode responseFormat = body.putObject("response_format");
        responseFormat.put("type", "json_object");
        ArrayNode messages = body.putArray("messages");
        messages.add(message("system", SYSTEM_PROMPT));
        messages.add(message("user", "原始查询：" + query + "\n最多生成 " + maxQueries + " 条改写查询。"));
        return body;
    }

    private ObjectNode message(String role, String content) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private List<String> parseQueries(JsonNode response, String originalQuery, int maxQueries) throws IOException {
        String content = response == null
                ? ""
                : response.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            return List.of();
        }
        JsonNode parsed = objectMapper.readTree(content);
        JsonNode queriesNode = parsed.isArray() ? parsed : parsed.path("queries");
        if (!queriesNode.isArray()) {
            return List.of();
        }
        Set<String> deduped = new LinkedHashSet<>();
        for (JsonNode queryNode : queriesNode) {
            addIfUseful(deduped, queryNode.asText(""), originalQuery);
            if (deduped.size() >= maxQueries) {
                break;
            }
        }
        return new ArrayList<>(deduped);
    }

    private void addIfUseful(Set<String> queries, String candidate, String originalQuery) {
        if (candidate == null) {
            return;
        }
        String value = candidate.strip();
        if (value.isBlank() || value.equals(originalQuery) || queries.contains(value)) {
            return;
        }
        queries.add(value);
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

    public interface QueryExpansionHttpClient {

        JsonNode post(String path, JsonNode body, Map<String, String> headers) throws IOException, InterruptedException;
    }

    private static final class JdkQueryExpansionHttpClient implements QueryExpansionHttpClient {

        private final OpenAiCompatibleQueryExpansionServiceProperties properties;
        private final ObjectMapper objectMapper;
        private final HttpClient httpClient;

        private JdkQueryExpansionHttpClient(
                OpenAiCompatibleQueryExpansionServiceProperties properties,
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
                throw new IOException("HTTP " + response.statusCode() + " from query expansion provider");
            }
            String responseBody = response.body();
            if (responseBody == null || responseBody.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(responseBody);
        }
    }
}
