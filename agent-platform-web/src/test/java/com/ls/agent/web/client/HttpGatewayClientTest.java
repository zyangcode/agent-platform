package com.ls.agent.web.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HttpGatewayClientTest {

    @Test
    void defaultHttpClientHasConnectTimeout() {
        HttpGatewayClient client = new HttpGatewayClient(
                new ObjectMapper(),
                "http://localhost:8081",
                "dev-internal-token"
        );

        assertThat(client.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
    }
}
