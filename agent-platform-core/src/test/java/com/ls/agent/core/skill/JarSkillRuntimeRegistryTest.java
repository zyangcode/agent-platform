package com.ls.agent.core.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.skill.application.DefaultSkillExecutor;
import com.ls.agent.core.skill.application.JarSkillRuntimeRegistry;
import com.ls.agent.core.skill.command.SkillExecuteCommand;
import com.ls.agent.core.skill.dto.SkillExecuteResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JarSkillRuntimeRegistryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void defaultSkillExecutorDelegatesJarSkillCallsToRuntimeRegistry() {
        JarSkillRuntimeRegistry registry = new JarSkillRuntimeRegistry();
        registry.register("jar_echo", command -> new SkillExecuteResult(
                true,
                command.skillCode(),
                objectMapper.createObjectNode().put("echo", command.arguments().path("text").asText()),
                null
        ));
        DefaultSkillExecutor executor = new DefaultSkillExecutor(objectMapper, registry);

        SkillExecuteResult result = executor.execute(new SkillExecuteCommand(
                1L,
                10001L,
                "jar_echo",
                objectMapper.createObjectNode().put("text", "hello")
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.output().path("echo").asText()).isEqualTo("hello");
    }
}
