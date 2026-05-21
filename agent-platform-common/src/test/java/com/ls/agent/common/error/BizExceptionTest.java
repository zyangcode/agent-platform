package com.ls.agent.common.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BizExceptionTest {

    @Test
    void fromErrorCodeKeepsCodeMessageAndHttpStatus() {
        BizException exception = new BizException(ErrorCode.AUTH_UNAUTHORIZED);

        assertThat(exception.getCode()).isEqualTo("AUTH_UNAUTHORIZED");
        assertThat(exception.getMessage()).isEqualTo("Unauthorized or login expired");
        assertThat(exception.getHttpStatus()).isEqualTo(401);
    }

    @Test
    void customMessageOverridesDefaultMessage() {
        BizException exception = new BizException(ErrorCode.PARAM_INVALID, "username must not be blank");

        assertThat(exception.getCode()).isEqualTo("PARAM_INVALID");
        assertThat(exception.getMessage()).isEqualTo("username must not be blank");
        assertThat(exception.getHttpStatus()).isEqualTo(400);
    }
}
