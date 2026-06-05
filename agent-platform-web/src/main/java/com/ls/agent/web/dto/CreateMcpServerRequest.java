package com.ls.agent.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record CreateMcpServerRequest(
        @NotBlank String name,
        @NotBlank String serverType,
        JsonNode connectionConfig
) {
}
