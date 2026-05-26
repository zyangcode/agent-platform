package com.ls.agent.core.agent.dto;

public record AgentToolEventDTO(
        String type,
        String toolType,
        String toolName,
        String content
) {
}
