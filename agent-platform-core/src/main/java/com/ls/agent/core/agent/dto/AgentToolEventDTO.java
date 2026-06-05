package com.ls.agent.core.agent.dto;

import java.util.Map;

public record AgentToolEventDTO(
        String type,
        String toolType,
        String toolName,
        String content,
        Map<String, Object> metadata
) {

    public AgentToolEventDTO(String type, String toolType, String toolName, String content) {
        this(type, toolType, toolName, content, Map.of());
    }

    public AgentToolEventDTO {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
