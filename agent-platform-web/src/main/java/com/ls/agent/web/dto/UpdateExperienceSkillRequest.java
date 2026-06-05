package com.ls.agent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateExperienceSkillRequest(
        @NotNull Long applicationId,
        @NotBlank String code,
        @NotBlank String name,
        String domain,
        List<String> triggerKeywords,
        @NotBlank String content
) {
    public UpdateExperienceSkillRequest {
        triggerKeywords = triggerKeywords == null ? List.of() : List.copyOf(triggerKeywords);
    }
}
