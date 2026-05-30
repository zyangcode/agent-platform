package com.ls.agent.core.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.skill.application.DefaultSkillExecutor;
import com.ls.agent.core.skill.command.SkillExecuteCommand;
import com.ls.agent.core.skill.dto.SkillExecuteResult;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSkillExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultSkillExecutor executor = new DefaultSkillExecutor(objectMapper);

    @Test
    void calculatorExecutesArithmeticExpression() {
        SkillExecuteResult result = executor.execute(new SkillExecuteCommand(
                1L,
                10001L,
                "calculator",
                objectMapper.createObjectNode().put("expression", "128 * 36 + 59")
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.output().get("result").asText()).isEqualTo("4667");
    }

    @Test
    void weatherUsesOpenMeteoResponses() {
        DefaultSkillExecutor realExecutor = new DefaultSkillExecutor(objectMapper, this::fakeWeatherHttpGet);

        SkillExecuteResult result = realExecutor.execute(new SkillExecuteCommand(
                1L,
                10001L,
                "weather",
                objectMapper.createObjectNode().put("city", "Chongqing")
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.output().get("source").asText()).isEqualTo("open-meteo");
        assertThat(result.output().get("city").asText()).isEqualTo("Chongqing");
        assertThat(result.output().get("country").asText()).isEqualTo("China");
        assertThat(result.output().get("temperatureC").asDouble()).isEqualTo(26.4);
        assertThat(result.output().get("summary").asText())
                .contains("Chongqing", "26.4C")
                .doesNotContain("mock");
    }

    @Test
    void searchUsesWikipediaOpenSearchResponse() {
        DefaultSkillExecutor realExecutor = new DefaultSkillExecutor(objectMapper, this::fakeSearchHttpGet);

        SkillExecuteResult result = realExecutor.execute(new SkillExecuteCommand(
                1L,
                10001L,
                "search",
                objectMapper.createObjectNode().put("query", "Agent platform")
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.output().get("source").asText()).isEqualTo("wikipedia");
        assertThat(result.output().get("results")).hasSize(2);
        assertThat(result.output().get("results").get(0).get("title").asText()).isEqualTo("Agent");
        assertThat(result.output().get("results").get(0).get("url").asText()).isEqualTo("https://en.wikipedia.org/wiki/Agent");
        assertThat(result.output().toString()).doesNotContain("Mock search result", "example.com");
    }

    @Test
    void searchFallsBackToWikipediaFullTextSearchWhenOpenSearchIsEmpty() {
        DefaultSkillExecutor realExecutor = new DefaultSkillExecutor(objectMapper, this::fakeEmptyOpenSearchThenQuerySearch);

        SkillExecuteResult result = realExecutor.execute(new SkillExecuteCommand(
                1L,
                10001L,
                "search",
                objectMapper.createObjectNode().put("query", "Agent platform")
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.output().get("source").asText()).isEqualTo("wikipedia");
        assertThat(result.output().get("results")).hasSize(1);
        assertThat(result.output().get("results").get(0).get("title").asText()).isEqualTo("Gemini Enterprise");
        assertThat(result.output().get("results").get(0).get("url").asText())
                .isEqualTo("https://en.wikipedia.org/wiki/Gemini_Enterprise");
    }

    private JsonNode fakeWeatherHttpGet(URI uri) {
        if ("geocoding-api.open-meteo.com".equals(uri.getHost())) {
            return objectMapper.createObjectNode()
                    .set("results", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                            .put("name", "Chongqing")
                            .put("country", "China")
                            .put("latitude", 29.56)
                            .put("longitude", 106.55)));
        }
        if ("api.open-meteo.com".equals(uri.getHost())) {
            return objectMapper.createObjectNode()
                    .set("current", objectMapper.createObjectNode()
                            .put("time", "2026-05-30T10:00")
                            .put("temperature_2m", 26.4)
                            .put("wind_speed_10m", 8.2)
                            .put("weather_code", 1));
        }
        throw new IllegalArgumentException("Unexpected URI: " + uri);
    }

    private JsonNode fakeSearchHttpGet(URI uri) {
        assertThat(uri.getHost()).isEqualTo("en.wikipedia.org");
        return objectMapper.createArrayNode()
                .add("Agent platform")
                .add(objectMapper.createArrayNode().add("Agent").add("Software agent"))
                .add(objectMapper.createArrayNode().add("Agent article").add("Software agent article"))
                .add(objectMapper.createArrayNode()
                        .add("https://en.wikipedia.org/wiki/Agent")
                        .add("https://en.wikipedia.org/wiki/Software_agent"));
    }

    private JsonNode fakeEmptyOpenSearchThenQuerySearch(URI uri) {
        assertThat(uri.getHost()).isEqualTo("en.wikipedia.org");
        if (uri.getQuery().contains("action=opensearch")) {
            return objectMapper.createArrayNode()
                    .add("Agent platform")
                    .add(objectMapper.createArrayNode())
                    .add(objectMapper.createArrayNode())
                    .add(objectMapper.createArrayNode());
        }
        if (uri.getQuery().contains("action=query")) {
            return objectMapper.createObjectNode()
                    .set("query", objectMapper.createObjectNode()
                            .set("search", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                                    .put("title", "Gemini Enterprise")
                                    .put("snippet", "AI agent platform"))));
        }
        throw new IllegalArgumentException("Unexpected URI: " + uri);
    }
}
