package com.ls.agent.core.identity.dto;

import java.util.List;

public record CurrentUserDTO(
        Long userId,
        Long tenantId,
        String username,
        String displayName,
        List<String> roles
) {
}
