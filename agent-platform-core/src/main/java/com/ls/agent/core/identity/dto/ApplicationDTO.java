package com.ls.agent.core.identity.dto;

import java.time.LocalDateTime;

public record ApplicationDTO(
        Long applicationId,
        String name,
        String description,
        String status,
        LocalDateTime createdAt
) {
}
