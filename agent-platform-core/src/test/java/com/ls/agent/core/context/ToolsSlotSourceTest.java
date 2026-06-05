package com.ls.agent.core.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.context.application.ToolsSlotSource;
import com.ls.agent.core.context.command.BuildAgentContextCommand;
import com.ls.agent.core.context.dto.ContextSlot;
import com.ls.agent.core.context.dto.ContextSlotContent;
import com.ls.agent.core.context.dto.ContextSlotKind;
import com.ls.agent.core.mcp.dto.McpToolDTO;
import com.ls.agent.core.skill.dto.SkillDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolsSlotSourceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fetchBuildsSkillsAndMcpToolsBlock() {
        ToolsSlotSource source = new ToolsSlotSource(
                List.of(skill("calculator", "Evaluate arithmetic expressions.")),
                List.of(mcpTool("read_file", "Read a demo file."))
        );

        ContextSlotContent content = source.fetch(
                ContextSlot.required(ContextSlotKind.TOOLS, 1_000),
                command()
        );

        assertThat(source.supports(ContextSlotKind.TOOLS)).isTrue();
        assertThat(source.supports(ContextSlotKind.EXPERIENCE)).isFalse();
        assertThat(content.kind()).isEqualTo(ContextSlotKind.TOOLS);
        assertThat(content.content())
                .contains("Available skills:")
                .contains("- calculator: Evaluate arithmetic expressions.")
                .contains("Available MCP tools:")
                .contains("- read_file: Read a demo file.");
        assertThat(content.usedTokens()).isPositive();
        assertThat(content.truncated()).isFalse();
    }

    @Test
    void fetchReturnsEmptyContentWhenNoToolsAvailable() {
        ToolsSlotSource source = new ToolsSlotSource(List.of(), List.of());

        ContextSlotContent content = source.fetch(
                ContextSlot.required(ContextSlotKind.TOOLS, 1_000),
                command()
        );

        assertThat(content.content()).isEmpty();
        assertThat(content.usedTokens()).isZero();
        assertThat(content.truncated()).isFalse();
    }

    private SkillDTO skill(String code, String description) {
        return new SkillDTO(
                1L,
                code,
                code,
                description,
                "BUILTIN",
                "GLOBAL",
                "ENABLED",
                objectMapper.createObjectNode().put("type", "object")
        );
    }

    private McpToolDTO mcpTool(String name, String description) {
        return new McpToolDTO(
                1L,
                1L,
                name,
                description,
                "AVAILABLE",
                objectMapper.createObjectNode().put("type", "object")
        );
    }

    private BuildAgentContextCommand command() {
        return new BuildAgentContextCommand(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "hello",
                1_000,
                null,
                null
        );
    }
}
