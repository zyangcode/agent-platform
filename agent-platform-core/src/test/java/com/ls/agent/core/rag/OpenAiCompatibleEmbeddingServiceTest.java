package com.ls.agent.core.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.rag.application.OpenAiCompatibleEmbeddingService;
import com.ls.agent.core.rag.application.OpenAiCompatibleEmbeddingServiceProperties;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleEmbeddingServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiCompatibleEmbeddingServiceProperties properties = new OpenAiCompatibleEmbeddingServiceProperties(
            true,
            "https://api.openai.com/v1",
            "sk-test",
            "text-embedding-3-small",
            3,
            2000
    );

    @Test
    void embedSendsOpenAiCompatibleRequestAndParsesVector() throws Exception {
        FakeEmbeddingHttpClient client = new FakeEmbeddingHttpClient(objectMapper);
        client.nextResponse = objectMapper.readTree("""
                {
                  "model": "text-embedding-3-small",
                  "data": [
                    {
                      "embedding": [0.1, 0.2, 0.3]
                    }
                  ]
                }
                """);
        OpenAiCompatibleEmbeddingService service = new OpenAiCompatibleEmbeddingService(properties, client, objectMapper);

        EmbeddingVectorDTO vector = service.embed("重庆天气适合打篮球吗");

        assertThat(vector.model()).isEqualTo("text-embedding-3-small");
        assertThat(vector.values()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(client.path).isEqualTo("/embeddings");
        assertThat(client.headers).containsEntry("Authorization", "Bearer sk-test");
        assertThat(client.headers).containsEntry("Accept", "application/json");
        assertThat(client.body.path("model").asText()).isEqualTo("text-embedding-3-small");
        assertThat(client.body.path("input").asText()).isEqualTo("重庆天气适合打篮球吗");
        assertThat(client.body.path("encoding_format").asText()).isEqualTo("float");
        assertThat(client.body.path("dimensions").asInt()).isEqualTo(3);
    }

    @Test
    void blankInputDoesNotCallProvider() {
        FakeEmbeddingHttpClient client = new FakeEmbeddingHttpClient(objectMapper);
        OpenAiCompatibleEmbeddingService service = new OpenAiCompatibleEmbeddingService(properties, client, objectMapper);

        EmbeddingVectorDTO vector = service.embed("  ");

        assertThat(vector.model()).isEqualTo("text-embedding-3-small");
        assertThat(vector.values()).isEmpty();
        assertThat(client.path).isNull();
    }

    @Test
    void providerFailureDegradesToEmptyVector() {
        FakeEmbeddingHttpClient client = new FakeEmbeddingHttpClient(objectMapper);
        client.fail = true;
        OpenAiCompatibleEmbeddingService service = new OpenAiCompatibleEmbeddingService(properties, client, objectMapper);

        EmbeddingVectorDTO vector = service.embed("basketball weather");

        assertThat(vector.model()).isEqualTo("text-embedding-3-small");
        assertThat(vector.values()).isEmpty();
    }

    private static final class FakeEmbeddingHttpClient implements OpenAiCompatibleEmbeddingService.EmbeddingHttpClient {

        private final ObjectMapper objectMapper;
        private String path;
        private JsonNode body;
        private Map<String, String> headers = new LinkedHashMap<>();
        private JsonNode nextResponse;
        private boolean fail;

        private FakeEmbeddingHttpClient(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            this.nextResponse = objectMapper.createObjectNode();
        }

        @Override
        public JsonNode post(String path, JsonNode body, Map<String, String> headers) throws IOException {
            this.path = path;
            this.body = body.deepCopy();
            this.headers = new LinkedHashMap<>(headers);
            if (fail) {
                throw new IOException("embedding provider down");
            }
            return nextResponse == null ? objectMapper.createObjectNode() : nextResponse;
        }
    }
}
