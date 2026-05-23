package com.ls.agent.core.model.provider;

import com.ls.agent.core.model.dto.ModelUsageDTO;

public record ProviderResponse(
        String assistantMessage,
        ModelUsageDTO usage
) {
}
