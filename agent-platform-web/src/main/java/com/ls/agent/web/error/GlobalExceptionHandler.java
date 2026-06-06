package com.ls.agent.web.error;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException exception) {
        return ResponseEntity
                .status(exception.getHttpStatus())
                .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .orElse(null);
        String message = fieldError == null
                ? ErrorCode.REQUEST_INVALID.getMessage()
                : fieldError.getField() + " is invalid";
        return ResponseEntity
                .status(ErrorCode.REQUEST_INVALID.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.REQUEST_INVALID.getCode(), message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestParameter(
            MissingServletRequestParameterException exception
    ) {
        return ResponseEntity
                .status(ErrorCode.REQUEST_INVALID.getHttpStatus())
                .body(ApiResponse.failure(
                        ErrorCode.REQUEST_INVALID.getCode(),
                        exception.getParameterName() + " is required"
                ));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("NOT_FOUND", "Resource not found"));
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncRequestTimeout(
            AsyncRequestTimeoutException exception,
            HttpServletResponse response
    ) {
        log.warn("Async web request timed out");
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE);
        if (isSseOrCommitted(response)) {
            builder.contentType(MediaType.TEXT_EVENT_STREAM);
        }
        return builder.build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception,
            HttpServletResponse response
    ) {
        log.error("Unexpected web request failure", exception);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        if (isSseOrCommitted(response)) {
            return ResponseEntity
                    .status(errorCode.getHttpStatus())
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .build();
        }
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(errorCode.getCode(), safeMessage(exception)));
    }

    private boolean isSseOrCommitted(HttpServletResponse response) {
        if (response == null) {
            return false;
        }
        String contentType = response.getContentType();
        return response.isCommitted()
                || (contentType != null && contentType.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    private String safeMessage(Exception exception) {
        if (exception == null) {
            return ErrorCode.INTERNAL_ERROR.getMessage();
        }
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
