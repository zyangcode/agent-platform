package com.ls.agent.core.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.mcp.dto.McpToolDTO;
import com.ls.agent.core.skill.dto.SkillDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DefaultAgentToolResolver implements AgentToolResolver {

    @Override
    public List<AgentToolDTO> resolve(AgentContextDTO context) {
        if (context == null) {
            return List.of();
        }
        List<AgentToolDTO> tools = new ArrayList<>();
        for (SkillDTO skill : safeSkills(context)) {
            tools.add(fromSkill(skill));
        }
        for (McpToolDTO tool : safeMcpTools(context)) {
            tools.add(fromMcpTool(tool));
        }
        return List.copyOf(tools);
    }

    private List<SkillDTO> safeSkills(AgentContextDTO context) {
        return context.availableSkills() == null ? List.of() : context.availableSkills();
    }

    private List<McpToolDTO> safeMcpTools(AgentContextDTO context) {
        return context.availableMcpTools() == null ? List.of() : context.availableMcpTools();
    }

    private AgentToolDTO fromSkill(SkillDTO skill) {
        return new AgentToolDTO(
                skill.code(),
                skill.name(),
                skill.description(),
                AgentToolSourceType.SKILL,
                skill.parameterSchema(),
                riskLevel(skill.parameterSchema()),
                readOnly(skill.parameterSchema()),
                resourceKeys(skill.parameterSchema())
        );
    }

    private AgentToolDTO fromMcpTool(McpToolDTO tool) {
        return new AgentToolDTO(
                tool.name(),
                tool.name(),
                tool.description(),
                AgentToolSourceType.MCP,
                tool.parameterSchema(),
                riskLevel(tool.parameterSchema()),
                readOnly(tool.parameterSchema()),
                resourceKeys(tool.parameterSchema())
        );
    }

    private boolean readOnly(JsonNode parameterSchema) {
        return parameterSchema != null
                && parameterSchema.has("x-readOnly")
                && parameterSchema.get("x-readOnly").asBoolean(false);
    }

    private AgentToolRiskLevel riskLevel(JsonNode parameterSchema) {
        if (parameterSchema == null || !parameterSchema.hasNonNull("x-riskLevel")) {
            return AgentToolRiskLevel.LOW;
        }
        try {
            return AgentToolRiskLevel.valueOf(parameterSchema.get("x-riskLevel").asText().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return AgentToolRiskLevel.LOW;
        }
    }

    private List<String> resourceKeys(JsonNode parameterSchema) {
        if (parameterSchema == null || !parameterSchema.has("x-resourceKeys") || !parameterSchema.get("x-resourceKeys").isArray()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (JsonNode item : parameterSchema.get("x-resourceKeys")) {
            String key = item.asText("");
            if (!key.isBlank()) {
                keys.add(key);
            }
        }
        return keys;
    }
}
