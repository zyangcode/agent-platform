package com.ls.agent.common.error;

import java.util.Objects;

/**
 * Business exception carrying a stable platform error code.
 */
public class BizException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public BizException(ErrorCode errorCode, String message) {
        super(Objects.requireNonNull(message, "message must not be null"));
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
