package com.ls.agent.web.dto;

import com.ls.agent.core.identity.dto.CurrentUserDTO;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        CurrentUserDTO user
) {
}
