package com.ls.agent.core.team.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record TeamTaskDTO(
        String id,
        String name,
        String description,
        String taskType,
        String suggestedTool,
        JsonNode arguments,
        List<String> dependsOn
) {
}
