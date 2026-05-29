package com.ls.agent.core.team.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record TeamRuntimeEventDTO(
        String type,
        String traceId,
        Integer step,
        String taskId,
        String toolName,
        String status,
        String message,
        JsonNode payload,
        Instant createdAt
) {
    public static final String TYPE_TEAM_START = "team_start";
    public static final String TYPE_TEAM_PLAN = "team_plan";
    public static final String TYPE_TEAM_TASK_START = "team_task_start";
    public static final String TYPE_TEAM_TOOL_CALL = "team_tool_call";
    public static final String TYPE_TEAM_TOOL_RESULT = "team_tool_result";
    public static final String TYPE_TEAM_TASK_RESULT = "team_task_result";
    public static final String TYPE_TEAM_REVIEW = "team_review";
    public static final String TYPE_TEAM_RETRY = "team_retry";
    public static final String TYPE_TEAM_FINAL = "team_final";

    public TeamRuntimeEventDTO {
        payload = payload == null ? null : payload.deepCopy();
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static TeamRuntimeEventDTO start(String traceId, Integer step, String message, JsonNode payload) {
        return of(TYPE_TEAM_START, traceId, step, null, null, null, message, payload);
    }

    public static TeamRuntimeEventDTO plan(String traceId, Integer step, String message, JsonNode payload) {
        return of(TYPE_TEAM_PLAN, traceId, step, null, null, null, message, payload);
    }

    public static TeamRuntimeEventDTO taskStart(String traceId, Integer step, String taskId, String message) {
        return of(TYPE_TEAM_TASK_START, traceId, step, taskId, null, null, message, null);
    }

    public static TeamRuntimeEventDTO toolCall(String traceId, Integer step, String taskId, String toolName) {
        return of(TYPE_TEAM_TOOL_CALL, traceId, step, taskId, toolName, null, null, null);
    }

    public static TeamRuntimeEventDTO toolResult(
            String traceId,
            Integer step,
            String taskId,
            String toolName,
            String status,
            JsonNode payload
    ) {
        return of(TYPE_TEAM_TOOL_RESULT, traceId, step, taskId, toolName, status, null, payload);
    }

    public static TeamRuntimeEventDTO taskResult(
            String traceId,
            Integer step,
            String taskId,
            String status,
            String message,
            JsonNode payload
    ) {
        return of(TYPE_TEAM_TASK_RESULT, traceId, step, taskId, null, status, message, payload);
    }

    public static TeamRuntimeEventDTO review(
            String traceId,
            Integer step,
            String status,
            String message,
            JsonNode payload
    ) {
        return of(TYPE_TEAM_REVIEW, traceId, step, null, null, status, message, payload);
    }

    public static TeamRuntimeEventDTO retry(String traceId, Integer step, String taskId, String message, JsonNode payload) {
        return of(TYPE_TEAM_RETRY, traceId, step, taskId, null, null, message, payload);
    }

    public static TeamRuntimeEventDTO finalAnswer(String traceId, Integer step, String message, JsonNode payload) {
        return of(TYPE_TEAM_FINAL, traceId, step, null, null, null, message, payload);
    }

    private static TeamRuntimeEventDTO of(
            String type,
            String traceId,
            Integer step,
            String taskId,
            String toolName,
            String status,
            String message,
            JsonNode payload
    ) {
        return new TeamRuntimeEventDTO(type, traceId, step, taskId, toolName, status, message, payload, null);
    }
}
