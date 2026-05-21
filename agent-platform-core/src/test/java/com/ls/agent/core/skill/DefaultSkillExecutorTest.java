package com.ls.agent.core.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.skill.application.DefaultSkillExecutor;
import com.ls.agent.core.skill.command.SkillExecuteCommand;
import com.ls.agent.core.skill.dto.SkillExecuteResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSkillExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultSkillExecutor executor = new DefaultSkillExecutor(objectMapper);

    @Test
    void calculatorExecutesArithmeticExpression() {
        SkillExecuteResult result = executor.execute(new SkillExecuteCommand(
                1L,
                10001L,
                "calculator",
                objectMapper.createObjectNode().put("expression", "128 * 36 + 59")
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.output().get("result").asText()).isEqualTo("4667");
    }

    @Test
    void weatherReturnsMockResult() {
        SkillExecuteResult result = executor.execute(new SkillExecuteCommand(
                1L,
                10001L,
                "weather",
                objectMapper.createObjectNode().put("city", "Chongqing")
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.output().get("summary").asText()).contains("Chongqing");
    }
}
