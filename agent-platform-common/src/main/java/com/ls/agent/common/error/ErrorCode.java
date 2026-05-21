package com.ls.agent.common.error;

/**
 * Platform-level error codes shared by entry modules.
 */
public enum ErrorCode {

    OK("OK", "success", 200),
    AUTH_UNAUTHORIZED("AUTH_UNAUTHORIZED", "Unauthorized or login expired", 401),
    AUTH_FORBIDDEN("AUTH_FORBIDDEN", "Access denied", 403),
    PARAM_INVALID("PARAM_INVALID", "Invalid request parameter", 400),
    REQUEST_INVALID("REQUEST_INVALID", "Invalid request", 400),
    API_KEY_INVALID("API_KEY_INVALID", "Invalid API key", 401),
    APPLICATION_DISABLED("APPLICATION_DISABLED", "Application is unavailable", 403),
    QUOTA_EXCEEDED("QUOTA_EXCEEDED", "Quota exceeded", 429),
    SECURITY_BLOCKED("SECURITY_BLOCKED", "Request blocked by security policy", 403),
    MODEL_INVOKE_FAILED("MODEL_INVOKE_FAILED", "Model invocation failed", 502),
    AGENT_MAX_STEPS_EXCEEDED("AGENT_MAX_STEPS_EXCEEDED", "Agent max steps exceeded", 500),
    SKILL_EXECUTE_FAILED("SKILL_EXECUTE_FAILED", "Skill execution failed", 500),
    MCP_TOOL_FAILED("MCP_TOOL_FAILED", "MCP tool invocation failed", 500),
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error", 500);

    private final String code;
    private final String message;
    private final int httpStatus;

    ErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
