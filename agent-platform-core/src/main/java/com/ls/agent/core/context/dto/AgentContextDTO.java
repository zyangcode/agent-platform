package com.ls.agent.core.context.dto;

import com.ls.agent.core.mcp.dto.McpToolDTO;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.skill.dto.SkillDTO;

import java.util.List;

public record AgentContextDTO(
        Long modelConfigId,
        ProfileDTO profile,
        List<ModelMessage> messages,
        List<SkillDTO> availableSkills,
        List<McpToolDTO> availableMcpTools,
        int estimatedTokens,
        boolean truncated
) {
    public AgentContextDTO {
        messages = messages == null ? List.of() : List.copyOf(messages);
        availableSkills = availableSkills == null ? List.of() : List.copyOf(availableSkills);
        availableMcpTools = availableMcpTools == null ? List.of() : List.copyOf(availableMcpTools);
    }
}
