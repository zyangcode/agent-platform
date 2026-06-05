package com.ls.agent.gateway.error;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayExceptionHandlerTest {

    private final GatewayExceptionHandler handler = new GatewayExceptionHandler();

    @Test
    void bizExceptionReturnsJsonBeforeStreamStarts() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = handler.handleBizException(
                new BizException(ErrorCode.QUOTA_EXCEEDED, "Token quota exceeded"),
                response
        );

        assertThat(result.getStatusCode().value()).isEqualTo(ErrorCode.QUOTA_EXCEEDED.getHttpStatus());
        assertThat(result.getBody()).isInstanceOf(ApiResponse.class);
        assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void bizExceptionDoesNotWriteJsonBodyWhenSseResponseAlreadyStarted() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

        var result = handler.handleBizException(
                new BizException(ErrorCode.REQUEST_INVALID, "Agent failed"),
                response
        );

        assertThat(result.getStatusCode().value()).isEqualTo(ErrorCode.REQUEST_INVALID.getHttpStatus());
        assertThat(result.getBody()).isNull();
        assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
    }

    @Test
    void asyncTimeoutDoesNotWriteJsonBodyWhenSseResponseAlreadyStarted() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

        var result = handler.handleAsyncRequestTimeout(new AsyncRequestTimeoutException(), response);

        assertThat(result.getStatusCode().is5xxServerError()).isTrue();
        assertThat(result.getBody()).isNull();
        assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
    }
}
