package com.ls.agent.core.mcp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public final class DemoWeatherMcpServer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DemoWeatherMcpServer() {
    }

    public static void main(String[] args) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode request = OBJECT_MAPPER.readTree(line);
                ObjectNode response = OBJECT_MAPPER.createObjectNode();
                response.put("jsonrpc", "2.0");
                response.set("id", request.path("id"));
                String method = request.path("method").asText("");
                if ("initialize".equals(method)) {
                    response.set("result", initializeResult());
                } else if ("tools/list".equals(method)) {
                    response.set("result", toolsListResult());
                } else if ("tools/call".equals(method)) {
                    response.set("result", callToolResult(request.path("params")));
                } else {
                    ObjectNode error = OBJECT_MAPPER.createObjectNode();
                    error.put("code", -32601);
                    error.put("message", "Unsupported method: " + method);
                    response.set("error", error);
                }
                writer.write(OBJECT_MAPPER.writeValueAsString(response));
                writer.newLine();
                writer.flush();
            }
        }
    }

    private static ObjectNode initializeResult() {
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        result.set("capabilities", OBJECT_MAPPER.createObjectNode()
                .set("tools", OBJECT_MAPPER.createObjectNode()));
        result.set("serverInfo", OBJECT_MAPPER.createObjectNode()
                .put("name", "agent-platform-demo-weather")
                .put("version", "1.0.0"));
        return result;
    }

    private static ObjectNode toolsListResult() {
        var tools = OBJECT_MAPPER.createArrayNode();
        tools.add(OBJECT_MAPPER.createObjectNode()
                .put("name", "weather.current")
                .put("description", "Get current weather by city")
                .set("inputSchema", OBJECT_MAPPER.createObjectNode()
                        .put("type", "object")
                        .set("properties", OBJECT_MAPPER.createObjectNode()
                                .set("city", OBJECT_MAPPER.createObjectNode()
                                        .put("type", "string")
                                        .put("description", "City name")))));
        return OBJECT_MAPPER.createObjectNode().set("tools", tools);
    }

    private static ObjectNode callToolResult(JsonNode params) {
        String toolName = params.path("name").asText("");
        if (!"weather.current".equals(toolName)) {
            ObjectNode errorResult = OBJECT_MAPPER.createObjectNode();
            errorResult.put("isError", true);
            errorResult.set("content", OBJECT_MAPPER.createArrayNode()
                    .add(OBJECT_MAPPER.createObjectNode()
                            .put("type", "text")
                            .put("text", "Unsupported tool: " + toolName)));
            return errorResult;
        }
        String city = normalizeCity(params.path("arguments").path("city").asText("重庆"));
        return weatherResult(city);
    }

    private static ObjectNode weatherResult(String city) {
        try {
            String normalized = city.strip();
            double lat;
            double lon;
            String name;
            java.util.Map<String, double[]> known = knownCities();
            if (known.containsKey(normalized)) {
                double[] coords = known.get(normalized);
                lat = coords[0]; lon = coords[1]; name = normalized;
            } else {
                String encoded = URLEncoder.encode(normalized, StandardCharsets.UTF_8);
                URI geoUri = URI.create("https://geocoding-api.open-meteo.com/v1/search?name=" + encoded + "&count=1&language=en&format=json");
                HttpClient geoClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
                HttpResponse<String> geoResp = geoClient.send(
                        HttpRequest.newBuilder(geoUri).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                JsonNode geo = OBJECT_MAPPER.readTree(geoResp.body());
                JsonNode first = geo.path("results").path(0);
                if (first.isMissingNode()) {
                    return errorResult("City not found: " + city);
                }
                lat = first.path("latitude").asDouble();
                lon = first.path("longitude").asDouble();
                name = first.path("name").asText(normalized);
            }
            URI forecastUri = URI.create("https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m,weather_code,wind_speed_10m,relative_humidity_2m"
                    + "&timezone=auto");
            HttpClient forecastClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<String> fcResp = forecastClient.send(
                    HttpRequest.newBuilder(forecastUri).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode current = OBJECT_MAPPER.readTree(fcResp.body()).path("current");
            double temp = current.path("temperature_2m").asDouble();
            int code = current.path("weather_code").asInt();
            double wind = current.path("wind_speed_10m").asDouble();
            int humidity = current.path("relative_humidity_2m").asInt();
            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            result.put("city", name);
            result.put("temperatureCelsius", temp);
            result.put("weatherCode", code);
            result.put("condition", weatherDescription(code));
            result.put("windSpeedKmh", wind);
            result.put("humidityPercent", humidity);
            result.put("summary", name + "当前" + weatherDescription(code)
                    + "，" + temp + "°C，湿度" + humidity + "%");
            result.put("source", "open-meteo");
            result.put("isError", false);
            return result;
        } catch (Exception ex) {
            return errorResult("Weather lookup failed: " + ex.getMessage());
        }
    }

    private static String weatherDescription(int code) {
        return switch (code) {
            case 0 -> "晴天"; case 1,2,3 -> "多云";
            case 45,48 -> "雾霾"; case 51,53,55 -> "小雨";
            case 61,63,65 -> "中雨"; case 71,73,75 -> "小雪";
            case 80,81,82 -> "阵雨"; case 95,96,99 -> "雷暴";
            default -> "多云";
        };
    }

    private static ObjectNode errorResult(String message) {
        ObjectNode error = OBJECT_MAPPER.createObjectNode();
        error.put("isError", true);
        error.put("content", OBJECT_MAPPER.createArrayNode()
                .add(OBJECT_MAPPER.createObjectNode().put("type", "text").put("text", message)));
        return error;
    }

    private static String normalizeCity(String city) {
        if (city == null || city.isBlank()) {
            return "重庆";
        }
        return city.strip();
    }

    private static java.util.Map<String, double[]> knownCities() {
        java.util.Map<String, double[]> cities = new java.util.LinkedHashMap<>();
        cities.put("重庆", new double[]{29.56, 106.55});
        cities.put("北京", new double[]{39.90, 116.41});
        cities.put("上海", new double[]{31.23, 121.47});
        cities.put("广州", new double[]{23.13, 113.26});
        cities.put("深圳", new double[]{22.54, 114.06});
        cities.put("成都", new double[]{30.57, 104.07});
        cities.put("杭州", new double[]{30.29, 120.15});
        cities.put("武汉", new double[]{30.59, 114.31});
        cities.put("西安", new double[]{34.34, 108.94});
        cities.put("南京", new double[]{32.06, 118.80});
        return cities;
    }
}
