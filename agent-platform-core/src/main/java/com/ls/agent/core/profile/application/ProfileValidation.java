package com.ls.agent.core.profile.application;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;

final class ProfileValidation {

    private ProfileValidation() {
    }

    static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, fieldName + " must not be blank");
        }
        return value.trim();
    }

    static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, fieldName + " must not be null");
        }
        return value;
    }
}
