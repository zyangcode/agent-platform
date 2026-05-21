package com.ls.agent.core.profile.dto;

public record ProfileMcpToolBindingDTO(
        Long mcpToolId,
        Boolean enabledByDefault
) {
}
