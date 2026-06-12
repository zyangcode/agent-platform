package com.ls.agent.core.skill;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.skill.application.JarSkillLoader;
import com.ls.agent.core.skill.application.JarSkillRuntimeRegistry;
import com.ls.agent.core.skill.command.SkillExecuteCommand;
import com.ls.agent.core.skill.entity.SkillArtifactEntity;
import com.ls.agent.core.skill.entity.SkillEntity;
import com.ls.agent.core.skill.entity.SkillVersionEntity;
import com.ls.agent.core.skill.mapper.SkillArtifactMapper;
import com.ls.agent.core.skill.mapper.SkillMapper;
import com.ls.agent.core.skill.mapper.SkillVersionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JarSkillLoaderTest {

    @TempDir
    Path tempDir;

    private final SkillMapper skillMapper = mock(SkillMapper.class);
    private final SkillVersionMapper skillVersionMapper = mock(SkillVersionMapper.class);
    private final SkillArtifactMapper skillArtifactMapper = mock(SkillArtifactMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void restoreEnabledJarSkillsLoadsCurrentVersionArtifactIntoRuntimeRegistry() throws Exception {
        Path jarPath = compileEchoSkillJar();
        SkillEntity skill = enabledJarSkill();
        SkillVersionEntity version = readyVersion();
        SkillArtifactEntity artifact = jarArtifact(jarPath);
        when(skillMapper.selectList(any(Wrapper.class))).thenReturn(List.of(skill));
        when(skillVersionMapper.selectById(201L)).thenReturn(version);
        when(skillArtifactMapper.selectOne(any(Wrapper.class))).thenReturn(artifact);
        JarSkillRuntimeRegistry runtimeRegistry = new JarSkillRuntimeRegistry();
        JarSkillLoader loader = new JarSkillLoader(runtimeRegistry, skillMapper, skillVersionMapper, skillArtifactMapper);

        loader.restoreEnabledJarSkills();

        try {
            var handler = runtimeRegistry.find("jar_echo");
            assertThat(handler).isPresent();
            var result = handler.get().execute(new SkillExecuteCommand(
                    1L,
                    10001L,
                    "jar_echo",
                    objectMapper.readTree("{\"text\":\"after-restart\"}")
            ));
            assertThat(result.success()).isTrue();
            assertThat(result.output().path("echo").asText()).isEqualTo("after-restart");
        } finally {
            runtimeRegistry.closeAll();
        }
    }

    private SkillEntity enabledJarSkill() {
        SkillEntity entity = new SkillEntity();
        entity.setId(101L);
        entity.setCode("jar_echo");
        entity.setSkillType("JAR");
        entity.setStatus("ENABLED");
        entity.setCurrentVersionId(201L);
        return entity;
    }

    private SkillVersionEntity readyVersion() {
        SkillVersionEntity entity = new SkillVersionEntity();
        entity.setId(201L);
        entity.setSkillId(101L);
        entity.setRuntimeConfig(objectMapper.createObjectNode()
                .put("runtime", "jar")
                .put("handlerClass", "com.example.EchoSkill"));
        entity.setStatus("READY");
        return entity;
    }

    private SkillArtifactEntity jarArtifact(Path jarPath) throws Exception {
        SkillArtifactEntity entity = new SkillArtifactEntity();
        entity.setId(301L);
        entity.setSkillVersionId(201L);
        entity.setArtifactType("JAR");
        entity.setStoragePath(jarPath.toString());
        entity.setFileName("jar-echo.jar");
        entity.setSizeBytes(Files.size(jarPath));
        entity.setChecksum("sha256:jar");
        return entity;
    }

    private Path compileEchoSkillJar() throws Exception {
        Path sourceDir = tempDir.resolve("src/com/example");
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);
        Path sourceFile = sourceDir.resolve("EchoSkill.java");
        Files.writeString(sourceFile, """
                package com.example;

                import com.fasterxml.jackson.databind.ObjectMapper;
                import com.fasterxml.jackson.databind.node.ObjectNode;
                import com.ls.agent.core.skill.api.JarSkillHandler;
                import com.ls.agent.core.skill.command.SkillExecuteCommand;
                import com.ls.agent.core.skill.dto.SkillExecuteResult;

                public class EchoSkill implements JarSkillHandler {
                    private final ObjectMapper objectMapper = new ObjectMapper();

                    @Override
                    public SkillExecuteResult execute(SkillExecuteCommand command) {
                        ObjectNode output = objectMapper.createObjectNode();
                        output.put("echo", command.arguments().path("text").asText());
                        return new SkillExecuteResult(true, command.skillCode(), output, null);
                    }
                }
                """, StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler must be available").isNotNull();
        int result = compiler.run(
                null,
                null,
                null,
                "-classpath",
                System.getProperty("java.class.path"),
                "-d",
                classesDir.toString(),
                sourceFile.toString()
        );
        assertThat(result).isZero();

        Path jarPath = tempDir.resolve("jar-echo.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            Path classFile = classesDir.resolve("com/example/EchoSkill.class");
            jar.putNextEntry(new JarEntry("com/example/EchoSkill.class"));
            Files.copy(classFile, jar);
            jar.closeEntry();
        }
        return jarPath;
    }
}
