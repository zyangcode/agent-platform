package com.ls.agent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRagDocumentRequest(
        @NotNull Long applicationId,
        Long profileId,
        @NotBlank String title,
        String sourceType,
        String sourceUri,
        @NotBlank String content,
        Integer chunkTokenBudget,
        Integer overlapTokens
) {
}
