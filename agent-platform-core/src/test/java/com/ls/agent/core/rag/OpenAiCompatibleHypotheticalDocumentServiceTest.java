package com.ls.agent.core.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.rag.application.OpenAiCompatibleHypotheticalDocumentService;
import com.ls.agent.core.rag.application.OpenAiCompatibleHypotheticalDocumentServiceProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleHypotheticalDocumentServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiCompatibleHypotheticalDocumentServiceProperties properties =
            new OpenAiCompatibleHypotheticalDocumentServiceProperties(
                    true,
                    "https://llm.example.com/v1/",
                    "sk-test",
                    "gpt-4o-mini",
                    0.2,
                    "/chat/completions",
                    3000
            );

    @Test
    void generatePostsQueryAndParsesJsonDocumentsFromChatCompletion() throws Exception {
        FakeHydeHttpClient client = new FakeHydeHttpClient(objectMapper);
        client.response = objectMapper.readTree("""
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"documents\\":[\\"Context retrieval timeout is handled with a bounded fallback.\\",\\"RAG recall should degrade gracefully when embeddings fail.\\"]}"
                      }
                    }
                  ]
                }
                """);
        OpenAiCompatibleHypotheticalDocumentService service =
                new OpenAiCompatibleHypotheticalDocumentService(properties, client, objectMapper);

        List<String> documents = service.generate("上下文检索超时怎么办", 1);

        assertThat(client.path).isEqualTo("/chat/completions");
        assertThat(client.headers)
                .containsEntry("Authorization", "Bearer sk-test")
                .containsEntry("Content-Type", "application/json");
        assertThat(client.body.path("model").asText()).isEqualTo("gpt-4o-mini");
        assertThat(client.body.path("temperature").asDouble()).isEqualTo(0.2);
        assertThat(client.body.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(client.body.path("messages"))
                .hasSize(2);
        assertThat(client.body.path("messages").get(1).path("content").asText())
                .contains("上下文检索超时怎么办")
                .contains("1");
        assertThat(documents)
                .containsExactly("Context retrieval timeout is handled with a bounded fallback.");
    }

    @Test
    void generateParsesPlainJsonArrayAndFiltersBlankOrOriginalDocuments() throws Exception {
        FakeHydeHttpClient client = new FakeHydeHttpClient(objectMapper);
        client.response = objectMapper.readTree("""
                {
                  "choices": [
                    {
                      "message": {
                        "content": "[\\"query\\",\\"\\",\\"Memory recall combines vector and keyword evidence.\\"]"
                      }
                    }
                  ]
                }
                """);
        OpenAiCompatibleHypotheticalDocumentService service =
                new OpenAiCompatibleHypotheticalDocumentService(properties, client, objectMapper);

        List<String> documents = service.generate("query", 3);

        assertThat(documents)
                .containsExactly("Memory recall combines vector and keyword evidence.");
    }

    @Test
    void generateFallsBackToEmptyListWhenProviderFailsOrDisabled() {
        FakeHydeHttpClient failingClient = new FakeHydeHttpClient(objectMapper);
        failingClient.failure = new IOException("llm unavailable");
        OpenAiCompatibleHypotheticalDocumentService service =
                new OpenAiCompatibleHypotheticalDocumentService(properties, failingClient, objectMapper);

        assertThat(service.generate("query", 2)).isEmpty();

        OpenAiCompatibleHypotheticalDocumentService disabled = new OpenAiCompatibleHypotheticalDocumentService(
                new OpenAiCompatibleHypotheticalDocumentServiceProperties(false, "https://llm.example.com/v1", "", "gpt-4o-mini", 0.2, "/chat/completions", 3000),
                new FakeHydeHttpClient(objectMapper),
                objectMapper
        );

        assertThat(disabled.generate("query", 2)).isEmpty();
    }

    private static final class FakeHydeHttpClient implements OpenAiCompatibleHypotheticalDocumentService.HydeHttpClient {

        private final ObjectMapper objectMapper;
        private String path;
        private JsonNode body;
        private Map<String, String> headers;
        private JsonNode response;
        private IOException failure;

        private FakeHydeHttpClient(ObjectMapper objectMapper) {
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
