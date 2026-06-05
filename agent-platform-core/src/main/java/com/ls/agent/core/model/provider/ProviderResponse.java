package com.ls.agent.core.model.provider;

import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.model.dto.ModelToolCallDTO;

import java.util.List;

public record ProviderResponse(
        String assistantMessage,
        ModelUsageDTO usage,
        List<ModelToolCallDTO> toolCalls
) {

    public ProviderResponse(String assistantMessage, ModelUsageDTO usage) {
        this(assistantMessage, usage, List.of());
    }

    public ProviderResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
