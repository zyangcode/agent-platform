package com.ls.agent.gateway.error;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

@RestControllerAdvice
public class GatewayExceptionHandler {

    @ExceptionHandler(BizException.class)
    ResponseEntity<ApiResponse<Void>> handleBizException(BizException ex, HttpServletResponse response) {
        if (isSseOrCommitted(response)) {
            return ResponseEntity.status(ex.getHttpStatus())
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .build();
        }
        return ResponseEntity.status(ex.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    ResponseEntity<Void> handleAsyncRequestTimeout(
            AsyncRequestTimeoutException ex,
            HttpServletResponse response
    ) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE);
        if (isSseOrCommitted(response)) {
            builder.contentType(MediaType.TEXT_EVENT_STREAM);
        }
        return builder.build();
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception ex, HttpServletResponse response) {
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        if (isSseOrCommitted(response)) {
            return ResponseEntity.status(errorCode.getHttpStatus())
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .build();
        }
        return ResponseEntity.status(errorCode.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(errorCode.getCode(), errorCode.getMessage()));
    }

    private boolean isSseOrCommitted(HttpServletResponse response) {
        if (response == null) {
            return false;
        }
        String contentType = response.getContentType();
        return response.isCommitted()
                || (contentType != null && contentType.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE));
    }
}
