package com.ls.agent.core.agent.hook;

import com.ls.agent.core.agent.tool.AgentToolRiskLevel;
import com.ls.agent.core.agent.tool.AgentToolSourceType;

import java.util.List;

public record ToolHookContext(
        String traceId,
        Long tenantId,
        Long applicationId,
        Long userId,
        Long profileId,
        int step,
        AgentToolSourceType sourceType,
        String toolName,
        AgentToolRiskLevel riskLevel,
        boolean readOnly,
        List<String> resourceKeys,
        String argumentsSummary
) {
    public ToolHookContext {
        resourceKeys = resourceKeys == null ? List.of() : List.copyOf(resourceKeys);
    }
}
