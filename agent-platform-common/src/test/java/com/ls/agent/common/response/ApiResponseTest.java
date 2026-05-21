package com.ls.agent.common.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void successWrapsDataWithOkCode() {
        ApiResponse<String> response = ApiResponse.success("value");

        assertThat(response.success()).isTrue();
        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.message()).isEqualTo("success");
        assertThat(response.data()).isEqualTo("value");
    }

    @Test
    void failureUsesProvidedErrorCodeAndMessage() {
        ApiResponse<Object> response = ApiResponse.failure("AUTH_UNAUTHORIZED", "Unauthorized or login expired");

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("AUTH_UNAUTHORIZED");
        assertThat(response.message()).isEqualTo("Unauthorized or login expired");
        assertThat(response.data()).isNull();
    }
}
