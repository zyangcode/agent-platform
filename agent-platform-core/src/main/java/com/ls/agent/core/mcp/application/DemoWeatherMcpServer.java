package com.ls.agent.core.mcp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

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
        WeatherSnapshot snapshot = snapshot(city);
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        result.put("city", city);
        result.put("temperatureCelsius", snapshot.temperatureCelsius());
        result.put("condition", snapshot.condition());
        result.put("humidityPercent", snapshot.humidityPercent());
        result.put("windLevel", snapshot.windLevel());
        result.put("basketballAdvice", snapshot.basketballAdvice());
        result.put("summary", city + "当前" + snapshot.condition()
                + "，约" + snapshot.temperatureCelsius() + "℃，湿度" + snapshot.humidityPercent()
                + "%，" + snapshot.basketballAdvice());
        result.put("source", "demo-mcp-weather");
        result.put("isError", false);
        return result;
    }

    private static WeatherSnapshot snapshot(String city) {
        String normalized = city.toLowerCase(Locale.ROOT);
        if (normalized.contains("重庆") || normalized.contains("chongqing")) {
            return new WeatherSnapshot(31, "多云偏闷热", 72, "2级", "适合傍晚打篮球，注意补水并避开正午高温。");
        }
        if (normalized.contains("北京") || normalized.contains("beijing")) {
            return new WeatherSnapshot(27, "晴", 38, "3级", "适合户外篮球，注意热身和防晒。");
        }
        if (normalized.contains("上海") || normalized.contains("shanghai")) {
            return new WeatherSnapshot(29, "阴", 68, "3级", "可以打篮球，湿度偏高时建议降低强度。");
        }
        if (normalized.contains("广州") || normalized.contains("guangzhou")) {
            return new WeatherSnapshot(32, "阵雨间歇", 78, "2级", "不建议室外篮球，优先选择室内场地。");
        }
        return new WeatherSnapshot(26, "多云", 55, "2级", "适合轻中等强度篮球，出发前再确认本地实时天气。");
    }

    private static String normalizeCity(String city) {
        if (city == null || city.isBlank()) {
            return "重庆";
        }
        return city.strip();
    }

    private record WeatherSnapshot(
            int temperatureCelsius,
            String condition,
            int humidityPercent,
            String windLevel,
            String basketballAdvice
    ) {
    }
}
