package com.ls.agent.core.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.mcp.dto.McpToolDTO;
import com.ls.agent.core.skill.dto.SkillDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultAgentToolResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultAgentToolResolver resolver = new DefaultAgentToolResolver();

    @Test
    void resolvesSkillsAndMcpToolsFromAgentContext() {
        SkillDTO skill = skill();
        McpToolDTO mcpTool = mcpTool();
        AgentContextDTO context = new AgentContextDTO(
                30001L,
                null,
                List.of(),
                List.of(skill),
                List.of(mcpTool),
                120,
                false
        );

        List<AgentToolDTO> tools = resolver.resolve(context);

        assertThat(tools).hasSize(2);
        assertThat(tools.get(0).name()).isEqualTo("calculator");
        assertThat(tools.get(0).displayName()).isEqualTo("Calculator");
        assertThat(tools.get(0).description()).isEqualTo("Evaluate arithmetic expressions.");
        assertThat(tools.get(0).sourceType()).isEqualTo(AgentToolSourceType.SKILL);
        assertThat(tools.get(0).parameterSchema()).isSameAs(skill.parameterSchema());
        assertThat(tools.get(0).riskLevel()).isEqualTo(AgentToolRiskLevel.LOW);

        assertThat(tools.get(1).name()).isEqualTo("read_file");
        assertThat(tools.get(1).displayName()).isEqualTo("read_file");
        assertThat(tools.get(1).description()).isEqualTo("Read a demo file.");
        assertThat(tools.get(1).sourceType()).isEqualTo(AgentToolSourceType.MCP);
        assertThat(tools.get(1).parameterSchema()).isSameAs(mcpTool.parameterSchema());
        assertThat(tools.get(1).riskLevel()).isEqualTo(AgentToolRiskLevel.LOW);
    }

    @Test
    void resolvesEmptyListWhenContextIsNull() {
        assertThat(resolver.resolve(null)).isEmpty();
    }

    @Test
    void treatsNullToolListsAsEmpty() {
        AgentContextDTO context = mock(AgentContextDTO.class);
        when(context.availableSkills()).thenReturn(null);
        when(context.availableMcpTools()).thenReturn(null);

        assertThat(resolver.resolve(context)).isEmpty();
    }

    private SkillDTO skill() {
        return new SkillDTO(
                1L,
                "calculator",
                "Calculator",
                "Evaluate arithmetic expressions.",
                "BUILTIN",
                "GLOBAL",
                "ENABLED",
                objectMapper.createObjectNode().put("type", "object")
        );
    }

    private McpToolDTO mcpTool() {
        return new McpToolDTO(
                1L,
                1L,
                "read_file",
                "Read a demo file.",
                "AVAILABLE",
                objectMapper.createObjectNode().put("type", "object")
        );
    }
}
