package com.ls.agent.core.profile.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;

import java.util.Locale;

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

    static String normalizeExecutionMode(String value) {
        if (value == null || value.isBlank()) {
            return ProfileConstants.EXECUTION_MODE_BASIC;
        }
        String normalized = value.trim().toUpperCase();
        if (!ProfileConstants.EXECUTION_MODE_BASIC.equals(normalized)
                && !ProfileConstants.EXECUTION_MODE_TEAM.equals(normalized)) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "executionMode must be BASIC or TEAM");
        }
        return normalized;
    }

    static JsonNode normalizeMemoryStrategy(JsonNode value, ObjectNode defaultValue) {
        JsonNode strategy = value == null ? defaultValue : value;
        if (strategy == null || strategy.isNull() || !strategy.hasNonNull("mode")) {
            return strategy;
        }
        String mode = strategy.path("mode").asText("").trim().toUpperCase(Locale.ROOT);
        if (!ProfileConstants.MEMORY_MODE_DISABLED.equals(mode)
                && !ProfileConstants.MEMORY_MODE_READ_ONLY.equals(mode)
                && !ProfileConstants.MEMORY_MODE_READ_WRITE.equals(mode)
                && !ProfileConstants.MEMORY_MODE_SESSION_ONLY.equals(mode)) {
            throw new BizException(ErrorCode.REQUEST_INVALID,
                    "memoryStrategy.mode must be DISABLED, READ_ONLY, READ_WRITE or SESSION_ONLY");
        }
        if (strategy instanceof ObjectNode objectNode) {
            objectNode.put("mode", mode);
        }
        return strategy;
    }
}
