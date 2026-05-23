package com.ls.agent.core.trace.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;

final class TraceValidation {

    private TraceValidation() {
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, field + " is required");
        }
        return value.strip();
    }

    static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, field + " is required");
        }
        return value;
    }

    static JsonNode objectOrEmpty(JsonNode value, ObjectMapper objectMapper) {
        return value == null ? objectMapper.createObjectNode() : value;
    }
}
