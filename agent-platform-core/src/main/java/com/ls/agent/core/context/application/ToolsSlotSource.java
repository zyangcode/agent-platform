package com.ls.agent.core.context.application;

import com.ls.agent.core.context.api.ContextSlotSource;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.ContextSlot;
import com.ls.agent.core.context.dto.ContextSlotContent;
import com.ls.agent.core.context.dto.ContextSlotKind;
import com.ls.agent.core.mcp.dto.McpToolDTO;
import com.ls.agent.core.skill.dto.SkillDTO;

import java.util.List;

public class ToolsSlotSource implements ContextSlotSource {

    private final List<SkillDTO> skills;
    private final List<McpToolDTO> mcpTools;

    public ToolsSlotSource(List<SkillDTO> skills, List<McpToolDTO> mcpTools) {
        this.skills = skills == null ? List.of() : List.copyOf(skills);
        this.mcpTools = mcpTools == null ? List.of() : List.copyOf(mcpTools);
    }

    @Override
    public boolean supports(ContextSlotKind kind) {
        return ContextSlotKind.TOOLS.equals(kind);
    }

    @Override
    public ContextSlotContent fetch(ContextSlot slot, BuildAgentContextCommand command) {
        if (!supports(slot.kind()) || (skills.isEmpty() && mcpTools.isEmpty())) {
            return ContextSlotContent.empty(slot.kind());
        }
        StringBuilder builder = new StringBuilder();
        if (!skills.isEmpty()) {
            builder.append("Available skills:\n");
            skills.forEach(skill -> builder
                    .append("- ")
                    .append(skill.code())
                    .append(": ")
                    .append(nullToEmpty(skill.description()))
                    .append('\n'));
            builder.append('\n');
        }
        if (!mcpTools.isEmpty()) {
            builder.append("Available MCP tools:\n");
            mcpTools.forEach(tool -> builder
                    .append("- ")
                    .append(tool.name())
                    .append(": ")
                    .append(nullToEmpty(tool.description()))
                    .append('\n'));
        }
        return new ContextSlotContent(ContextSlotKind.TOOLS, builder.toString(), estimateTokens(builder.toString()), false);
    }

    static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
