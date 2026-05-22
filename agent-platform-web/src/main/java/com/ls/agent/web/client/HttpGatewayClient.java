package com.ls.agent.web.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class HttpGatewayClient implements GatewayClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String gatewayBaseUrl;
    private final String internalToken;

    @Autowired
    public HttpGatewayClient(
            ObjectMapper objectMapper,
            @Value("${web.gateway.internal-base-url:http://localhost:8081}") String gatewayBaseUrl,
            @Value("${web.gateway.internal-token:dev-internal-token}") String internalToken
    ) {
        this(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build(), objectMapper, gatewayBaseUrl, internalToken);
    }

    HttpGatewayClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String gatewayBaseUrl,
            String internalToken
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.gatewayBaseUrl = removeTrailingSlash(gatewayBaseUrl);
        this.internalToken = internalToken;
    }

    @Override
    public void streamTest(OutputStream output) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri("/internal/ai/stream-test"))
                .header("X-Internal-Token", internalToken)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .GET()
                .build();
        sendAndCopy(request, output);
    }

    @Override
    public void chatStream(InternalChatStreamRequest requestBody, OutputStream output) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri("/internal/ai/chat/stream"))
                .header("X-Internal-Token", internalToken)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(toJson(requestBody), StandardCharsets.UTF_8))
                .build();
        sendAndCopy(request, output);
    }

    private void sendAndCopy(HttpRequest request, OutputStream output) throws IOException {
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Gateway request interrupted");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Gateway request failed");
        }
        try (InputStream input = response.body()) {
            copyStreaming(input, output);
        }
    }

    private void copyStreaming(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
            output.flush();
        }
    }

    private String toJson(InternalChatStreamRequest requestBody) {
        try {
            return objectMapper.writeValueAsString(requestBody);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Gateway request serialization failed");
        }
    }

    private URI uri(String path) {
        return URI.create(gatewayBaseUrl + path);
    }

    private String removeTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8081";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    Duration connectTimeout() {
        return httpClient.connectTimeout().orElse(null);
    }
}
