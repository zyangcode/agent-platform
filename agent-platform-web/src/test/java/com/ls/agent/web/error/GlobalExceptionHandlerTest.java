package com.ls.agent.web.error;

import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedExceptionReturnsJsonForNormalRequest() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = handler.handleUnexpectedException(new IllegalStateException("boom"), response);

        assertThat(result.getStatusCode().value()).isEqualTo(ErrorCode.INTERNAL_ERROR.getHttpStatus());
        assertThat(result.getBody()).isInstanceOf(ApiResponse.class);
        assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void unexpectedExceptionDoesNotWriteJsonBodyWhenSseResponseAlreadyStarted() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

        var result = handler.handleUnexpectedException(new IllegalStateException("boom"), response);

        assertThat(result.getStatusCode().value()).isEqualTo(ErrorCode.INTERNAL_ERROR.getHttpStatus());
        assertThat(result.getBody()).isNull();
        assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
    }
}
