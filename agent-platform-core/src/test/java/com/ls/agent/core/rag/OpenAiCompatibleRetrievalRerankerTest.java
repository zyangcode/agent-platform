package com.ls.agent.core.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.rag.application.OpenAiCompatibleRetrievalReranker;
import com.ls.agent.core.rag.application.OpenAiCompatibleRetrievalRerankerProperties;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleRetrievalRerankerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiCompatibleRetrievalRerankerProperties properties =
            new OpenAiCompatibleRetrievalRerankerProperties(
                    true,
                    "https://rerank.example.com/v1/",
                    "sk-test",
                    "rerank-v1",
                    "/rerank",
                    3000
            );

    @Test
    void rerankPostsQueryAndDocumentsThenUsesReturnedIndexes() {
        FakeRerankerHttpClient client = new FakeRerankerHttpClient(objectMapper);
        client.response = objectMapper.createObjectNode()
                .set("results", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("index", 1)
                                .put("relevance_score", 0.98))
                        .add(objectMapper.createObjectNode()
                                .put("index", 0)
                                .put("relevance_score", 0.42)));
        OpenAiCompatibleRetrievalReranker reranker =
                new OpenAiCompatibleRetrievalReranker(properties, client, objectMapper);
        List<RagSearchResultDTO> candidates = List.of(
                result(1L, "Context timeout", "Context retrieval timeout details.", 0.4),
                result(2L, "Qdrant scope", "Qdrant global scope details.", 0.2)
        );

        List<RagSearchResultDTO> reranked = reranker.rerank("global scope", candidates, 2);

        assertThat(client.path).isEqualTo("/rerank");
        assertThat(client.headers)
                .containsEntry("Authorization", "Bearer sk-test")
                .containsEntry("Content-Type", "application/json");
        assertThat(client.body.path("model").asText()).isEqualTo("rerank-v1");
        assertThat(client.body.path("query").asText()).isEqualTo("global scope");
        assertThat(client.body.path("top_n").asInt()).isEqualTo(2);
        assertThat(client.body.path("documents"))
                .hasSize(2);
        assertThat(client.body.path("documents").get(0).asText())
                .contains("Context timeout")
                .contains("Context retrieval timeout details.");
        assertThat(reranked)
                .extracting(RagSearchResultDTO::chunkId)
                .containsExactly(2L, 1L);
        assertThat(reranked)
                .extracting(RagSearchResultDTO::score)
                .containsExactly(0.98, 0.42);
    }

    @Test
    void rerankFallsBackToOriginalCandidatesWhenProviderFails() {
        FakeRerankerHttpClient client = new FakeRerankerHttpClient(objectMapper);
        client.failure = new IOException("rerank unavailable");
        OpenAiCompatibleRetrievalReranker reranker =
                new OpenAiCompatibleRetrievalReranker(properties, client, objectMapper);
        List<RagSearchResultDTO> candidates = List.of(
                result(1L, "First", "first content", 0.4),
                result(2L, "Second", "second content", 0.2)
        );

        List<RagSearchResultDTO> reranked = reranker.rerank("query", candidates, 1);

        assertThat(reranked)
                .extracting(RagSearchResultDTO::chunkId)
                .containsExactly(1L);
    }

    @Test
    void rerankReturnsOriginalCandidatesWhenDisabledOrMissingApiKey() {
        OpenAiCompatibleRetrievalReranker disabled = new OpenAiCompatibleRetrievalReranker(
                new OpenAiCompatibleRetrievalRerankerProperties(false, "https://rerank.example.com/v1", "", "rerank-v1", "/rerank", 3000),
                new FakeRerankerHttpClient(objectMapper),
                objectMapper
        );
        List<RagSearchResultDTO> candidates = List.of(
                result(1L, "First", "first content", 0.4),
                result(2L, "Second", "second content", 0.2)
        );

        List<RagSearchResultDTO> reranked = disabled.rerank("query", candidates, 1);

        assertThat(reranked)
                .extracting(RagSearchResultDTO::chunkId)
                .containsExactly(1L);
    }

    private RagSearchResultDTO result(Long chunkId, String title, String content, double score) {
        return new RagSearchResultDTO(1000L + chunkId, chunkId, title, content, "kb://" + chunkId, score);
    }

    private static final class FakeRerankerHttpClient implements OpenAiCompatibleRetrievalReranker.RerankerHttpClient {

        private final ObjectMapper objectMapper;
        private String path;
        private JsonNode body;
        private Map<String, String> headers;
        private JsonNode response;
        private IOException failure;

        private FakeRerankerHttpClient(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public JsonNode post(String path, JsonNode body, Map<String, String> headers) throws IOException {
            this.path = path;
            this.body = body;
            this.headers = headers;
            if (failure != null) {
                throw failure;
            }
            return response == null ? objectMapper.createObjectNode() : response;
        }
    }
}
