package com.ls.agent.core.team.dto;

import com.ls.agent.core.model.dto.ModelInvokeResult;

import java.util.List;

public record TeamReviewResultDTO(
        ReviewResultDTO reviewResult,
        List<ModelInvokeResult> modelInvocations
) {
    public TeamReviewResultDTO {
        modelInvocations = modelInvocations == null ? List.of() : List.copyOf(modelInvocations);
    }
}
