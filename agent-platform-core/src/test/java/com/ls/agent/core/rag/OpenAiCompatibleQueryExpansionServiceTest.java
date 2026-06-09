package com.ls.agent.core.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.rag.application.OpenAiCompatibleQueryExpansionService;
import com.ls.agent.core.rag.application.OpenAiCompatibleQueryExpansionServiceProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleQueryExpansionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiCompatibleQueryExpansionServiceProperties properties =
            new OpenAiCompatibleQueryExpansionServiceProperties(
                    true,
                    "https://llm.example.com/v1/",
                    "sk-test",
                    "gpt-4o-mini",
                    0.1,
                    "/chat/completions",
                    3000
            );

    @Test
    void expandPostsQueryAndParsesJsonQueriesFromChatCompletion() throws Exception {
        FakeQueryExpansionHttpClient client = new FakeQueryExpansionHttpClient(objectMapper);
        client.response = objectMapper.readTree("""
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"queries\\":[\\"context retrieval timeout\\",\\"context build deadline\\",\\"rag recall timeout\\"]}"
                      }
                    }
                  ]
                }
                """);
        OpenAiCompatibleQueryExpansionService service =
                new OpenAiCompatibleQueryExpansionService(properties, client, objectMapper);

        List<String> queries = service.expand("上下文检索超时怎么办", 2);

        assertThat(client.path).isEqualTo("/chat/completions");
        assertThat(client.headers)
                .containsEntry("Authorization", "Bearer sk-test")
                .containsEntry("Content-Type", "application/json");
        assertThat(client.body.path("model").asText()).isEqualTo("gpt-4o-mini");
        assertThat(client.body.path("temperature").asDouble()).isEqualTo(0.1);
        assertThat(client.body.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(client.body.path("messages"))
                .hasSize(2);
        assertThat(client.body.path("messages").get(1).path("content").asText())
                .contains("上下文检索超时怎么办")
                .contains("2");
        assertThat(queries)
                .containsExactly("context retrieval timeout", "context build deadline");
    }

    @Test
    void expandParsesPlainJsonArrayAndRemovesOriginalOrBlankQueries() throws Exception {
        FakeQueryExpansionHttpClient client = new FakeQueryExpansionHttpClient(objectMapper);
        client.response = objectMapper.readTree("""
                {
                  "choices": [
                    {
                      "message": {
                        "content": "[\\"memory recall\\",\\"query\\",\\"\\",\\"rag memory recall\\"]"
                      }
                    }
                  ]
                }
                """);
        OpenAiCompatibleQueryExpansionService service =
                new OpenAiCompatibleQueryExpansionService(properties, client, objectMapper);

        List<String> queries = service.expand("query", 5);

        assertThat(queries)
                .containsExactly("memory recall", "rag memory recall");
    }

    @Test
    void expandFallsBackToEmptyListWhenProviderFailsOrDisabled() {
        FakeQueryExpansionHttpClient failingClient = new FakeQueryExpansionHttpClient(objectMapper);
        failingClient.failure = new IOException("llm unavailable");
        OpenAiCompatibleQueryExpansionService service =
                new OpenAiCompatibleQueryExpansionService(properties, failingClient, objectMapper);

        assertThat(service.expand("query", 3)).isEmpty();

        OpenAiCompatibleQueryExpansionService disabled = new OpenAiCompatibleQueryExpansionService(
                new OpenAiCompatibleQueryExpansionServiceProperties(false, "https://llm.example.com/v1", "", "gpt-4o-mini", 0.1, "/chat/completions", 3000),
                new FakeQueryExpansionHttpClient(objectMapper),
                objectMapper
        );

        assertThat(disabled.expand("query", 3)).isEmpty();
    }

    private static final class FakeQueryExpansionHttpClient implements OpenAiCompatibleQueryExpansionService.QueryExpansionHttpClient {

        private final ObjectMapper objectMapper;
        private String path;
        private JsonNode body;
        private Map<String, String> headers;
        private JsonNode response;
        private IOException failure;

        private FakeQueryExpansionHttpClient(ObjectMapper objectMapper) {
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
