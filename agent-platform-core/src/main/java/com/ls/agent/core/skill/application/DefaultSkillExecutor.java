package com.ls.agent.core.skill.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.skill.api.SkillExecutor;
import com.ls.agent.core.skill.command.SkillExecuteCommand;
import com.ls.agent.core.skill.dto.SkillExecuteResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class DefaultSkillExecutor implements SkillExecutor {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(8);

    private final ObjectMapper objectMapper;
    private final HttpJsonClient httpJsonClient;

    @Autowired
    public DefaultSkillExecutor(ObjectMapper objectMapper) {
        this(objectMapper, new JdkHttpJsonClient(objectMapper));
    }

    public DefaultSkillExecutor(ObjectMapper objectMapper, HttpJsonClient httpJsonClient) {
        this.objectMapper = objectMapper;
        this.httpJsonClient = httpJsonClient;
    }

    @Override
    public SkillExecuteResult execute(SkillExecuteCommand command) {
        String code = command.skillCode();
        if ("calculator".equals(code)) {
            return calculate(command);
        }
        if ("weather".equals(code)) {
            return weather(command);
        }
        if ("search".equals(code)) {
            return search(command);
        }
        throw new BizException(ErrorCode.SKILL_EXECUTE_FAILED, "Unsupported skill: " + code);
    }

    private SkillExecuteResult calculate(SkillExecuteCommand command) {
        String expression = requiredText(command, "expression");
        BigDecimal value = new ExpressionParser(expression).parse();
        ObjectNode output = objectMapper.createObjectNode();
        output.put("result", format(value));
        return new SkillExecuteResult(true, command.skillCode(), output, null);
    }

    private SkillExecuteResult weather(SkillExecuteCommand command) {
        String city = requiredText(command, "city");
        try {
            JsonLocation location = geocode(city);
            URI forecastUri = URI.create("https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + location.latitude()
                    + "&longitude=" + location.longitude()
                    + "&current=temperature_2m,weather_code,wind_speed_10m"
                    + "&timezone=auto");
            var forecast = httpJsonClient.get(forecastUri);
            var current = forecast.path("current");
            if (current.isMissingNode()) {
                return failed(command, "Weather provider returned no current weather data");
            }

            double temperature = current.path("temperature_2m").asDouble();
            double windSpeed = current.path("wind_speed_10m").asDouble();
            int weatherCode = current.path("weather_code").asInt();
            String description = weatherDescription(weatherCode);

            ObjectNode output = objectMapper.createObjectNode();
            output.put("source", "open-meteo");
            output.put("city", location.name());
            output.put("country", location.country());
            output.put("latitude", location.latitude());
            output.put("longitude", location.longitude());
            output.put("time", current.path("time").asText(""));
            output.put("temperatureC", temperature);
            output.put("windSpeedKmh", windSpeed);
            output.put("weatherCode", weatherCode);
            output.put("weather", description);
            output.put("summary", location.name() + " current weather: " + description
                    + ", " + formatDecimal(temperature) + "C, wind "
                    + formatDecimal(windSpeed) + " km/h");
            return new SkillExecuteResult(true, command.skillCode(), output, null);
        } catch (Exception ex) {
            restoreInterrupt(ex);
            return failed(command, "Weather lookup failed: " + safeMessage(ex));
        }
    }

    private JsonLocation geocode(String city) throws IOException, InterruptedException {
        URI uri = URI.create("https://geocoding-api.open-meteo.com/v1/search"
                + "?name=" + encode(city)
                + "&count=1&language=en&format=json");
        var response = httpJsonClient.get(uri);
        var results = response.path("results");
        if (!results.isArray() || results.isEmpty()) {
            throw new BizException(ErrorCode.SKILL_EXECUTE_FAILED, "No location found for city: " + city);
        }
        var first = results.get(0);
        return new JsonLocation(
                first.path("name").asText(city),
                first.path("country").asText(""),
                first.path("latitude").asDouble(),
                first.path("longitude").asDouble()
        );
    }

    private SkillExecuteResult search(SkillExecuteCommand command) {
        String query = requiredText(command, "query");
        try {
            URI uri = URI.create("https://en.wikipedia.org/w/api.php"
                    + "?action=opensearch&format=json&limit=5&namespace=0&search=" + encode(query));
            var response = httpJsonClient.get(uri);
            if (!response.isArray() || response.size() < 4) {
                return failed(command, "Search provider returned an unexpected response");
            }

            var results = openSearchResults(response);
            if (results.isEmpty()) {
                results = querySearchResults(query);
            }

            ObjectNode output = objectMapper.createObjectNode();
            output.put("source", "wikipedia");
            output.put("query", query);
            output.set("results", results);
            return new SkillExecuteResult(true, command.skillCode(), output, null);
        } catch (Exception ex) {
            restoreInterrupt(ex);
            return failed(command, "Search failed: " + safeMessage(ex));
        }
    }

    private com.fasterxml.jackson.databind.node.ArrayNode openSearchResults(com.fasterxml.jackson.databind.JsonNode response) {
        var titles = response.get(1);
        var descriptions = response.get(2);
        var urls = response.get(3);
        var results = objectMapper.createArrayNode();
        for (int index = 0; titles != null && index < titles.size(); index++) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("title", titles.get(index).asText());
            item.put("snippet", descriptions != null && descriptions.size() > index
                    ? descriptions.get(index).asText()
                    : "");
            item.put("url", urls != null && urls.size() > index ? urls.get(index).asText() : "");
            results.add(item);
        }
        return results;
    }

    private com.fasterxml.jackson.databind.node.ArrayNode querySearchResults(String query) throws IOException, InterruptedException {
        URI uri = URI.create("https://en.wikipedia.org/w/api.php"
                + "?action=query&list=search&format=json&srlimit=5&srsearch=" + encode(query));
        var response = httpJsonClient.get(uri);
        var search = response.path("query").path("search");
        var results = objectMapper.createArrayNode();
        if (!search.isArray()) {
            return results;
        }
        for (var itemNode : search) {
            String title = itemNode.path("title").asText();
            ObjectNode item = objectMapper.createObjectNode();
            item.put("title", title);
            item.put("snippet", stripHtml(itemNode.path("snippet").asText("")));
            item.put("url", "https://en.wikipedia.org/wiki/" + encodePathSegment(title.replace(' ', '_')));
            results.add(item);
        }
        return results;
    }

    private String stripHtml(String value) {
        return value == null ? "" : value.replaceAll("<[^>]+>", "");
    }

    private SkillExecuteResult failed(SkillExecuteCommand command, String message) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("error", message);
        return new SkillExecuteResult(false, command.skillCode(), output, message);
    }

    private String weatherDescription(int code) {
        return switch (code) {
            case 0 -> "clear sky";
            case 1, 2, 3 -> "partly cloudy";
            case 45, 48 -> "fog";
            case 51, 53, 55, 56, 57 -> "drizzle";
            case 61, 63, 65, 66, 67 -> "rain";
            case 71, 73, 75, 77 -> "snow";
            case 80, 81, 82 -> "rain showers";
            case 85, 86 -> "snow showers";
            case 95, 96, 99 -> "thunderstorm";
            default -> "weather code " + code;
        };
    }

    private String requiredText(SkillExecuteCommand command, String field) {
        if (command.arguments() == null || command.arguments().get(field) == null
                || command.arguments().get(field).asText().isBlank()) {
            throw new BizException(ErrorCode.SKILL_EXECUTE_FAILED, "Missing skill argument: " + field);
        }
        return command.arguments().get(field).asText();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String formatDecimal(double value) {
        return BigDecimal.valueOf(value)
                .setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private void restoreInterrupt(Exception ex) {
        if (ex instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private String format(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() <= 0) {
            return normalized.toPlainString();
        }
        return normalized.setScale(Math.min(normalized.scale(), 10), RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static final class ExpressionParser {

        private final String input;
        private int position;

        private ExpressionParser(String input) {
            this.input = input;
        }

        private BigDecimal parse() {
            BigDecimal result = parseExpression();
            skipWhitespace();
            if (position != input.length()) {
                throw new BizException(ErrorCode.SKILL_EXECUTE_FAILED, "Invalid calculator expression");
            }
            return result;
        }

        private BigDecimal parseExpression() {
            BigDecimal result = parseTerm();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    result = result.add(parseTerm());
                } else if (match('-')) {
                    result = result.subtract(parseTerm());
                } else {
                    return result;
                }
            }
        }

        private BigDecimal parseTerm() {
            BigDecimal result = parseFactor();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    result = result.multiply(parseFactor());
                } else if (match('/')) {
                    result = result.divide(parseFactor(), 10, RoundingMode.HALF_UP);
                } else {
                    return result;
                }
            }
        }

        private BigDecimal parseFactor() {
            skipWhitespace();
            if (match('+')) {
                return parseFactor();
            }
            if (match('-')) {
                return parseFactor().negate();
            }
            if (match('(')) {
                BigDecimal result = parseExpression();
                if (!match(')')) {
                    throw new BizException(ErrorCode.SKILL_EXECUTE_FAILED, "Invalid calculator expression");
                }
                return result;
            }
            return parseNumber();
        }

        private BigDecimal parseNumber() {
            skipWhitespace();
            int start = position;
            while (position < input.length()) {
                char current = input.charAt(position);
                if ((current >= '0' && current <= '9') || current == '.') {
                    position++;
                } else {
                    break;
                }
            }
            if (start == position) {
                throw new BizException(ErrorCode.SKILL_EXECUTE_FAILED, "Invalid calculator expression");
            }
            return new BigDecimal(input.substring(start, position));
        }

        private boolean match(char expected) {
            skipWhitespace();
            if (position < input.length() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
                position++;
            }
        }
    }

    @FunctionalInterface
    public interface HttpJsonClient {

        com.fasterxml.jackson.databind.JsonNode get(URI uri) throws IOException, InterruptedException;
    }

    private static final class JdkHttpJsonClient implements HttpJsonClient {

        private final ObjectMapper objectMapper;
        private final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();

        private JdkHttpJsonClient(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode get(URI uri) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(HTTP_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", "agent-platform-skill/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode() + " from " + uri.getHost());
            }
            return objectMapper.readTree(response.body());
        }
    }

    private record JsonLocation(
            String name,
            String country,
            double latitude,
            double longitude
    ) {
    }
}
