package com.ls.agent.core.team.dto;

import com.ls.agent.core.model.dto.ModelInvokeResult;

import java.util.List;

public record TeamPlanResultDTO(
        TaskPlanDTO plan,
        List<ModelInvokeResult> modelInvocations
) {
    public TeamPlanResultDTO {
        modelInvocations = modelInvocations == null ? List.of() : List.copyOf(modelInvocations);
    }
}
