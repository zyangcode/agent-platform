package com.ls.agent.core.agent.tool;

import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.mcp.dto.McpToolDTO;
import com.ls.agent.core.skill.dto.SkillDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
                AgentToolRiskLevel.LOW
        );
    }

    private AgentToolDTO fromMcpTool(McpToolDTO tool) {
        return new AgentToolDTO(
                tool.name(),
                tool.name(),
                tool.description(),
                AgentToolSourceType.MCP,
                tool.parameterSchema(),
                AgentToolRiskLevel.LOW
        );
    }
}
