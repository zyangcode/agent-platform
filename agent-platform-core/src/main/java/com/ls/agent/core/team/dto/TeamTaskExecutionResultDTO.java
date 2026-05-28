package com.ls.agent.core.team.dto;

import com.ls.agent.core.agent.tool.AgentToolDispatchResult;
import com.ls.agent.core.model.dto.ModelInvokeResult;

import java.util.List;

public record TeamTaskExecutionResultDTO(
        ExecutionResultDTO executionResult,
        List<ModelInvokeResult> modelInvocations,
        List<AgentToolDispatchResult> toolResults
) {
    public TeamTaskExecutionResultDTO {
        modelInvocations = modelInvocations == null ? List.of() : List.copyOf(modelInvocations);
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
    }
}
