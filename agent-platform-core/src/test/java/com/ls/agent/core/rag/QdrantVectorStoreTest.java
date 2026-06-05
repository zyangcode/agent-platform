package com.ls.agent.core.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.rag.application.QdrantVectorStore;
import com.ls.agent.core.rag.application.QdrantVectorStoreProperties;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;
import com.ls.agent.core.rag.dto.VectorSearchQueryDTO;
import com.ls.agent.core.rag.dto.VectorSearchResultDTO;
import com.ls.agent.core.rag.dto.VectorStoreDocumentDTO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QdrantVectorStoreTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QdrantVectorStoreProperties properties = new QdrantVectorStoreProperties(
            true,
            "http://localhost:6333",
            "rag_chunks_test",
            3,
            "Cosine",
            2000
    );

    @Test
    void upsertWritesPointWithScopedPayload() throws Exception {
        FakeQdrantHttpClient client = new FakeQdrantHttpClient(objectMapper);
        QdrantVectorStore store = new QdrantVectorStore(properties, client, objectMapper);

        store.upsert(new VectorStoreDocumentDTO(
                "vec-1",
                1L,
                20001L,
                10001L,
                50001L,
                90001L,
                91001L,
                new EmbeddingVectorDTO("mock-embedding", new float[]{0.1f, 0.2f, 0.3f})
        ));

        assertThat(client.requests).hasSize(3);
        CapturedRequest collectionRequest = client.requests.get(0);
        assertThat(collectionRequest.method).isEqualTo("GET");
        assertThat(collectionRequest.path).isEqualTo("/collections/rag_chunks_test/exists");

        collectionRequest = client.requests.get(1);
        assertThat(collectionRequest.method).isEqualTo("PUT");
        assertThat(collectionRequest.path).isEqualTo("/collections/rag_chunks_test");
        assertThat(collectionRequest.body.path("vectors").path("size").asInt()).isEqualTo(3);
        assertThat(collectionRequest.body.path("vectors").path("distance").asText()).isEqualTo("Cosine");

        CapturedRequest request = client.requests.get(2);
        assertThat(request.method).isEqualTo("PUT");
        assertThat(request.path).isEqualTo("/collections/rag_chunks_test/points?wait=true");
        JsonNode point = request.body.path("points").get(0);
        assertThat(point.path("id").asText()).hasSize(36);
        assertThat(point.path("id").asText()).isNotEqualTo("vec-1");
        assertThat(point.path("vector")).hasSize(3);
        JsonNode payload = point.path("payload");
        assertThat(payload.path("vector_id").asText()).isEqualTo("vec-1");
        assertThat(payload.path("source_type").asText()).isEqualTo("rag");
        assertThat(payload.path("tenant_id").asLong()).isEqualTo(1L);
        assertThat(payload.path("application_id").asLong()).isEqualTo(20001L);
        assertThat(payload.path("owner_user_id").asLong()).isEqualTo(10001L);
        assertThat(payload.path("profile_id").asLong()).isEqualTo(50001L);
        assertThat(payload.path("document_id").asLong()).isEqualTo(90001L);
        assertThat(payload.path("chunk_id").asLong()).isEqualTo(91001L);
        assertThat(payload.path("embedding_model").asText()).isEqualTo("mock-embedding");
    }

    @Test
    void searchSendsScopedFilterAndMapsReturnedPoints() throws Exception {
        FakeQdrantHttpClient client = new FakeQdrantHttpClient(objectMapper);
        client.nextResponse = objectMapper.readTree("""
                {
                  "result": [
                    {
                      "id": "vec-1",
                      "score": 0.87,
                      "payload": {
                        "vector_id": "vec-1",
                        "document_id": 90001,
                        "chunk_id": 91001
                      }
                    }
                  ]
                }
                """);
        QdrantVectorStore store = new QdrantVectorStore(properties, client, objectMapper);

        List<VectorSearchResultDTO> results = store.search(new VectorSearchQueryDTO(
                1L,
                20001L,
                10001L,
                50001L,
                new EmbeddingVectorDTO("mock-embedding", new float[]{0.4f, 0.5f, 0.6f}),
                3
        ));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).vectorId()).isEqualTo("vec-1");
        assertThat(results.get(0).documentId()).isEqualTo(90001L);
        assertThat(results.get(0).chunkId()).isEqualTo(91001L);
        assertThat(results.get(0).score()).isEqualTo(0.87);
        CapturedRequest request = client.requests.get(2);
        assertThat(request.method).isEqualTo("POST");
        assertThat(request.path).isEqualTo("/collections/rag_chunks_test/points/search");
        assertThat(request.body.path("limit").asInt()).isEqualTo(3);
        assertThat(request.body.path("vector")).hasSize(3);
        assertThat(filterKeys(request.body)).containsExactly(
                "source_type",
                "tenant_id",
                "application_id",
                "owner_user_id",
                "profile_id"
        );
        assertThat(filterValue(request.body, "source_type")).isEqualTo("rag");
    }

    @Test
    void deleteByDocumentDeletesOnlyMatchingScopedDocument() throws Exception {
        FakeQdrantHttpClient client = new FakeQdrantHttpClient(objectMapper);
        QdrantVectorStore store = new QdrantVectorStore(properties, client, objectMapper);

        int deleted = store.deleteByDocument(1L, 20001L, 10001L, 50001L, 90001L);

        assertThat(deleted).isZero();
        CapturedRequest request = client.requests.get(2);
        assertThat(request.method).isEqualTo("POST");
        assertThat(request.path).isEqualTo("/collections/rag_chunks_test/points/delete?wait=true");
        assertThat(filterKeys(request.body)).containsExactly(
                "source_type",
                "tenant_id",
                "application_id",
                "owner_user_id",
                "profile_id",
                "document_id"
        );
        assertThat(filterValue(request.body, "source_type")).isEqualTo("rag");
    }

    @Test
    void memorySearchSendsMemorySourceTypeFilter() throws Exception {
        FakeQdrantHttpClient client = new FakeQdrantHttpClient(objectMapper);
        QdrantVectorStore store = new QdrantVectorStore(properties, client, objectMapper);

        store.search(new VectorSearchQueryDTO(
                "memory",
                1L,
                20001L,
                10001L,
                50001L,
                new EmbeddingVectorDTO("mock-embedding", new float[]{0.4f, 0.5f, 0.6f}),
                3
        ));

        CapturedRequest request = client.requests.get(2);
        assertThat(filterValue(request.body, "source_type")).isEqualTo("memory");
    }

    @Test
    void memoryDeleteSendsMemorySourceTypeFilter() throws Exception {
        FakeQdrantHttpClient client = new FakeQdrantHttpClient(objectMapper);
        QdrantVectorStore store = new QdrantVectorStore(properties, client, objectMapper);

        store.deleteByDocument("memory", 1L, 20001L, 10001L, 50001L, 88L);

        CapturedRequest request = client.requests.get(2);
        assertThat(request.path).isEqualTo("/collections/rag_chunks_test/points/delete?wait=true");
        assertThat(filterValue(request.body, "source_type")).isEqualTo("memory");
        assertThat(filterValue(request.body, "document_id")).isEqualTo("88");
    }

    @Test
    void clientFailureDegradesWithoutBreakingCaller() {
        FakeQdrantHttpClient client = new FakeQdrantHttpClient(objectMapper);
        client.fail = true;
        QdrantVectorStore store = new QdrantVectorStore(properties, client, objectMapper);

        store.upsert(new VectorStoreDocumentDTO(
                "vec-1",
                1L,
                20001L,
                10001L,
                50001L,
                90001L,
                91001L,
                new EmbeddingVectorDTO("mock-embedding", new float[]{0.1f})
        ));

        assertThat(store.search(new VectorSearchQueryDTO(
                1L,
                20001L,
                10001L,
                50001L,
                new EmbeddingVectorDTO("mock-embedding", new float[]{0.1f}),
                3
        ))).isEmpty();
        assertThat(store.deleteByDocument(1L, 20001L, 10001L, 50001L, 90001L)).isZero();
    }

    @Test
    void collectionInitializationRunsOnlyOnceBeforeOperations() {
        FakeQdrantHttpClient client = new FakeQdrantHttpClient(objectMapper);
        QdrantVectorStore store = new QdrantVectorStore(properties, client, objectMapper);

        store.upsert(point("vec-1"));
        store.upsert(point("vec-2"));

        assertThat(client.requests).extracting(CapturedRequest::path).containsExactly(
                "/collections/rag_chunks_test/exists",
                "/collections/rag_chunks_test",
                "/collections/rag_chunks_test/points?wait=true",
                "/collections/rag_chunks_test/points?wait=true"
        );
    }

    @Test
    void existingCollectionSkipsCreateBeforeOperation() throws Exception {
        FakeQdrantHttpClient client = new FakeQdrantHttpClient(objectMapper);
        client.responseByPath.put("/collections/rag_chunks_test/exists", objectMapper.readTree("""
                {"result":{"exists":true}}
                """));
        QdrantVectorStore store = new QdrantVectorStore(properties, client, objectMapper);

        store.upsert(point("vec-1"));

        assertThat(client.requests).extracting(CapturedRequest::path).containsExactly(
                "/collections/rag_chunks_test/exists",
                "/collections/rag_chunks_test/points?wait=true"
        );
    }

    @Test
    void collectionInitializationFailureDegradesOperation() {
        FakeQdrantHttpClient client = new FakeQdrantHttpClient(objectMapper);
        client.failPaths.add("/collections/rag_chunks_test");
        QdrantVectorStore store = new QdrantVectorStore(properties, client, objectMapper);

        store.upsert(point("vec-1"));

        assertThat(client.requests).extracting(CapturedRequest::path).containsExactly(
                "/collections/rag_chunks_test/exists",
                "/collections/rag_chunks_test"
        );
    }

    private List<String> filterKeys(JsonNode body) {
        List<String> keys = new ArrayList<>();
        for (JsonNode condition : body.path("filter").path("must")) {
            keys.add(condition.path("key").asText());
        }
        return keys;
    }

    private String filterValue(JsonNode body, String key) {
        for (JsonNode condition : body.path("filter").path("must")) {
            if (key.equals(condition.path("key").asText())) {
                return condition.path("match").path("value").asText();
            }
        }
        return "";
    }

    private VectorStoreDocumentDTO point(String vectorId) {
        return new VectorStoreDocumentDTO(
                vectorId,
                1L,
                20001L,
                10001L,
                50001L,
                90001L,
                91001L,
                new EmbeddingVectorDTO("mock-embedding", new float[]{0.1f, 0.2f, 0.3f})
        );
    }

    private static final class FakeQdrantHttpClient implements QdrantVectorStore.QdrantHttpClient {

        private final ObjectMapper objectMapper;
        private final List<CapturedRequest> requests = new ArrayList<>();
        private final List<String> failPaths = new ArrayList<>();
        private final Map<String, JsonNode> responseByPath = new HashMap<>();
        private JsonNode nextResponse;
        private boolean fail;

        private FakeQdrantHttpClient(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            this.nextResponse = objectMapper.createObjectNode().put("status", "ok");
        }

        @Override
        public JsonNode send(String method, String path, JsonNode body) throws IOException {
            requests.add(new CapturedRequest(method, path, body.deepCopy()));
            if (fail) {
                throw new IOException("qdrant down");
            }
            if (failPaths.contains(path)) {
                throw new IOException("qdrant collection init failed");
            }
            if (responseByPath.containsKey(path)) {
                return responseByPath.get(path);
            }
            if (path.endsWith("/exists")) {
                return objectMapper.readTree("""
                        {"result":{"exists":false}}
                        """);
            }
            return nextResponse == null ? objectMapper.createObjectNode() : nextResponse;
        }
    }

    private record CapturedRequest(String method, String path, JsonNode body) {
    }
}
