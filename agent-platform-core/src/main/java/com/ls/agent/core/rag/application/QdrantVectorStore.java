package com.ls.agent.core.rag.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.core.rag.api.VectorStore;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;
import com.ls.agent.core.rag.dto.VectorSearchQueryDTO;
import com.ls.agent.core.rag.dto.VectorSearchResultDTO;
import com.ls.agent.core.rag.dto.VectorStoreDocumentDTO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QdrantVectorStore implements VectorStore {

    private final QdrantVectorStoreProperties properties;
    private final QdrantHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile boolean collectionReady;

    public QdrantVectorStore(QdrantVectorStoreProperties properties, ObjectMapper objectMapper) {
        this(properties, new JdkQdrantHttpClient(properties, objectMapper), objectMapper);
    }

    public QdrantVectorStore(
            QdrantVectorStoreProperties properties,
            QdrantHttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void upsert(VectorStoreDocumentDTO document) {
        if (!usable() || document == null || document.vectorId().isBlank() || empty(document.vector())) {
            return;
        }
        if (!ensureCollection()) {
            return;
        }
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode points = root.putArray("points");
            ObjectNode point = points.addObject();
            point.put("id", qdrantPointId(document.vectorId()));
            point.set("vector", vectorNode(document.vector()));
            ObjectNode payload = point.putObject("payload");
            payload.put("vector_id", document.vectorId());
            payload.put("source_type", document.sourceType());
            putLong(payload, "tenant_id", document.tenantId());
            putLong(payload, "application_id", document.applicationId());
            putLong(payload, "owner_user_id", document.ownerUserId());
            putLong(payload, "profile_id", document.profileId());
            putLong(payload, "document_id", document.documentId());
            putLong(payload, "chunk_id", document.chunkId());
            payload.put("embedding_model", document.vector().model());

            httpClient.send("PUT", "/collections/" + properties.collectionName() + "/points?wait=true", root);
        } catch (Exception ex) {
            restoreInterrupt(ex);
        }
    }

    @Override
    public List<VectorSearchResultDTO> search(VectorSearchQueryDTO query) {
        if (!usable() || query == null || query.topK() <= 0 || empty(query.queryVector())) {
            return List.of();
        }
        if (!ensureCollection()) {
            return List.of();
        }
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.set("vector", vectorNode(query.queryVector()));
            root.put("limit", query.topK());
            root.put("with_payload", true);
            root.set("filter", scopedFilter(query.tenantId(), query.applicationId(), query.ownerUserId(),
                    query.profileId(), null, query.sourceType()));

            JsonNode response = httpClient.send(
                    "POST",
                    "/collections/" + properties.collectionName() + "/points/search",
                    root
            );
            return mapSearchResults(response);
        } catch (Exception ex) {
            restoreInterrupt(ex);
            return List.of();
        }
    }

    @Override
    public int deleteByDocument(
            String sourceType,
            Long tenantId,
            Long applicationId,
            Long ownerUserId,
            Long profileId,
            Long documentId
    ) {
        if (!usable() || documentId == null) {
            return 0;
        }
        if (!ensureCollection()) {
            return 0;
        }
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.set("filter", scopedFilter(tenantId, applicationId, ownerUserId, profileId, documentId, sourceType));
            root.put("wait", true);
            httpClient.send("POST", "/collections/" + properties.collectionName() + "/points/delete?wait=true", root);
        } catch (Exception ex) {
            restoreInterrupt(ex);
        }
        return 0;
    }

    private boolean ensureCollection() {
        if (collectionReady) {
            return true;
        }
        if (properties.dimension() <= 0) {
            return false;
        }
        synchronized (this) {
            if (collectionReady) {
                return true;
            }
            try {
                if (!collectionExists()) {
                    ObjectNode root = objectMapper.createObjectNode();
                    ObjectNode vectors = root.putObject("vectors");
                    vectors.put("size", properties.dimension());
                    vectors.put("distance", properties.distance());
                    httpClient.send("PUT", "/collections/" + properties.collectionName(), root);
                }
                collectionReady = true;
                return true;
            } catch (Exception ex) {
                restoreInterrupt(ex);
                return false;
            }
        }
    }

    private boolean collectionExists() throws IOException, InterruptedException {
        JsonNode response = httpClient.send(
                "GET",
                "/collections/" + properties.collectionName() + "/exists",
                objectMapper.createObjectNode()
        );
        return response.path("result").path("exists").asBoolean(false);
    }

    private List<VectorSearchResultDTO> mapSearchResults(JsonNode response) {
        JsonNode result = response == null ? null : response.path("result");
        if (result == null || !result.isArray()) {
            return List.of();
        }
        List<VectorSearchResultDTO> results = new ArrayList<>();
        for (JsonNode point : result) {
            JsonNode payload = point.path("payload");
            Long documentId = asNullableLong(payload.path("document_id"));
            Long chunkId = asNullableLong(payload.path("chunk_id"));
            if (documentId == null || chunkId == null) {
                continue;
            }
            results.add(new VectorSearchResultDTO(
                    payload.path("vector_id").asText(point.path("id").asText("")),
                    documentId,
                    chunkId,
                    point.path("score").asDouble(0.0)
            ));
        }
        return results;
    }

    private ObjectNode scopedFilter(
            Long tenantId,
            Long applicationId,
            Long ownerUserId,
            Long profileId,
            Long documentId,
            String sourceType
    ) {
        ObjectNode filter = objectMapper.createObjectNode();
        ArrayNode must = filter.putArray("must");
        addMatch(must, "source_type", sourceType);
        addMatch(must, "tenant_id", tenantId);
        addMatch(must, "application_id", applicationId);
        addMatch(must, "owner_user_id", ownerUserId);
        addMatch(must, "profile_id", profileId);
        addMatch(must, "document_id", documentId);
        return filter;
    }

    private void addMatch(ArrayNode must, String key, Long value) {
        if (value == null) {
            return;
        }
        ObjectNode condition = must.addObject();
        condition.put("key", key);
        ObjectNode match = condition.putObject("match");
        match.put("value", value);
    }

    private void addMatch(ArrayNode must, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        ObjectNode condition = must.addObject();
        condition.put("key", key);
        ObjectNode match = condition.putObject("match");
        match.put("value", value);
    }

    private ArrayNode vectorNode(EmbeddingVectorDTO vector) {
        ArrayNode values = objectMapper.createArrayNode();
        for (float value : vector.values()) {
            values.add(value);
        }
        return values;
    }

    private boolean usable() {
        return properties != null && properties.enabled();
    }

    private boolean empty(EmbeddingVectorDTO vector) {
        return vector == null || vector.values().length == 0;
    }

    private void putLong(ObjectNode node, String fieldName, Long value) {
        if (value == null) {
            node.putNull(fieldName);
            return;
        }
        node.put(fieldName, value);
    }

    private Long asNullableLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.canConvertToLong()) {
            return null;
        }
        return node.asLong();
    }

    private String qdrantPointId(String vectorId) {
        return UUID.nameUUIDFromBytes(vectorId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void restoreInterrupt(Exception ex) {
        if (ex instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    public interface QdrantHttpClient {

        JsonNode send(String method, String path, JsonNode body) throws IOException, InterruptedException;
    }

    private static final class JdkQdrantHttpClient implements QdrantHttpClient {

        private final QdrantVectorStoreProperties properties;
        private final ObjectMapper objectMapper;
        private final HttpClient httpClient;

        private JdkQdrantHttpClient(QdrantVectorStoreProperties properties, ObjectMapper objectMapper) {
            this.properties = properties;
            this.objectMapper = objectMapper;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(properties.timeoutMs()))
                    .build();
        }

        @Override
        public JsonNode send(String method, String path, JsonNode body) throws IOException, InterruptedException {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.baseUrl() + path))
                    .timeout(Duration.ofMillis(properties.timeoutMs()))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "agent-platform-rag/1.0");
            String json = objectMapper.writeValueAsString(body == null ? objectMapper.createObjectNode() : body);
            if ("PUT".equalsIgnoreCase(method)) {
                builder.PUT(HttpRequest.BodyPublishers.ofString(json));
            } else if ("DELETE".equalsIgnoreCase(method)) {
                builder.DELETE();
            } else if ("GET".equalsIgnoreCase(method)) {
                builder.GET();
            } else {
                builder.POST(HttpRequest.BodyPublishers.ofString(json));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode() + " from Qdrant");
            }
            String responseBody = response.body();
            if (responseBody == null || responseBody.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(responseBody);
        }
    }
}
