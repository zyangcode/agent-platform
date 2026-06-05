package com.ls.agent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateExperienceSkillRequest(
        @NotNull Long applicationId,
        Long profileId,
        @NotBlank String code,
        @NotBlank String name,
        String domain,
        List<String> triggerKeywords,
        @NotBlank String content
) {
    public CreateExperienceSkillRequest {
        triggerKeywords = triggerKeywords == null ? List.of() : List.copyOf(triggerKeywords);
    }
}
