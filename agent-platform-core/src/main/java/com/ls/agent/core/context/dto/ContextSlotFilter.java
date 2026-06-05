package com.ls.agent.core.context.dto;

import java.time.Duration;
import java.util.List;

public record ContextSlotFilter(
        List<String> categories,
        List<String> requireTags,
        Double minScore,
        Integer topK,
        Duration maxAge
) {

    public ContextSlotFilter {
        categories = categories == null ? List.of() : List.copyOf(categories);
        requireTags = requireTags == null ? List.of() : List.copyOf(requireTags);
    }

    public static ContextSlotFilter empty() {
        return new ContextSlotFilter(List.of(), List.of(), null, null, null);
    }
}
