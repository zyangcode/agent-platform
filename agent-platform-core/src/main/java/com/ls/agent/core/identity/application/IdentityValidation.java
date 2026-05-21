package com.ls.agent.core.identity.application;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;

final class IdentityValidation {

    private IdentityValidation() {
    }

    static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, fieldName + " must not be blank");
        }
        return value.trim();
    }
}
