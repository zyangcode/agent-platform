package com.ls.agent.core.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAgentToolCallValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultAgentToolCallValidator validator = new DefaultAgentToolCallValidator(objectMapper);

    @Test
    void acceptsAuthorizedToolAndNormalizesNullArguments() {
        AgentToolValidationResult result = validator.validate(
                new AgentToolCall(AgentToolSourceType.SKILL, "calculator", null),
                List.of(tool("calculator", AgentToolSourceType.SKILL))
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.tool().name()).isEqualTo("calculator");
        assertThat(result.call().arguments().isObject()).isTrue();
    }

    @Test
    void rejectsUnknownToolWithObservation() {
        AgentToolValidationResult result = validator.validate(
                new AgentToolCall(AgentToolSourceType.SKILL, "calculator", objectMapper.createObjectNode()),
                List.of()
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.observation()).contains("[tool unavailable]", "skill:calculator");
    }

    @Test
    void rejectsNonObjectArgumentsWithObservation() {
        AgentToolValidationResult result = validator.validate(
                new AgentToolCall(AgentToolSourceType.MCP, "read_file", objectMapper.createArrayNode()),
                List.of(tool("read_file", AgentToolSourceType.MCP))
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.observation()).contains("[tool invalid arguments]", "mcp:read_file");
    }

    private AgentToolDTO tool(String name, AgentToolSourceType sourceType) {
        return new AgentToolDTO(
                name,
                name,
                name + " tool",
                sourceType,
                objectMapper.createObjectNode(),
                AgentToolRiskLevel.LOW
        );
    }
}
