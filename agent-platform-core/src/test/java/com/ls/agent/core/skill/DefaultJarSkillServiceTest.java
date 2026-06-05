package com.ls.agent.core.skill;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.skill.application.DefaultJarSkillService;
import com.ls.agent.core.skill.application.JarSkillLoader;
import com.ls.agent.core.skill.application.JarSkillRuntimeRegistry;
import com.ls.agent.core.skill.command.RegisterJarSkillCommand;
import com.ls.agent.core.skill.command.SkillExecuteCommand;
import com.ls.agent.core.skill.entity.SkillArtifactEntity;
import com.ls.agent.core.skill.entity.SkillEntity;
import com.ls.agent.core.skill.entity.SkillVersionEntity;
import com.ls.agent.core.skill.mapper.SkillArtifactMapper;
import com.ls.agent.core.skill.mapper.SkillMapper;
import com.ls.agent.core.skill.mapper.SkillVersionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultJarSkillServiceTest {

    @TempDir
    Path tempDir;

    private final SkillMapper skillMapper = mock(SkillMapper.class);
    private final SkillVersionMapper skillVersionMapper = mock(SkillVersionMapper.class);
    private final SkillArtifactMapper skillArtifactMapper = mock(SkillArtifactMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultJarSkillService service =
            new DefaultJarSkillService(skillMapper, skillVersionMapper, skillArtifactMapper, objectMapper);

    @Test
    void registerJarSkillCreatesEnabledSkillReadyVersionAndArtifactFromManifest() {
        when(skillMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(skillMapper.insert(any(SkillEntity.class))).thenAnswer(invocation -> {
            SkillEntity entity = invocation.getArgument(0);
            entity.setId(101L);
            return 1;
        });
        when(skillVersionMapper.insert(any(SkillVersionEntity.class))).thenAnswer(invocation -> {
            SkillVersionEntity entity = invocation.getArgument(0);
            entity.setId(201L);
            return 1;
        });

        var result = service.register(command(validManifest()));

        assertThat(result.skillId()).isEqualTo(101L);
        assertThat(result.skillCode()).isEqualTo("jar_echo");
        assertThat(result.status()).isEqualTo("ENABLED");
        assertThat(result.versionStatus()).isEqualTo("READY");

        ArgumentCaptor<SkillEntity> skillCaptor = ArgumentCaptor.forClass(SkillEntity.class);
        verify(skillMapper).insert(skillCaptor.capture());
        assertThat(skillCaptor.getValue().getTenantId()).isEqualTo(1L);
        assertThat(skillCaptor.getValue().getOwnerUserId()).isEqualTo(10001L);
        assertThat(skillCaptor.getValue().getSkillType()).isEqualTo("JAR");
        assertThat(skillCaptor.getValue().getScope()).isEqualTo("PERSONAL");
        assertThat(skillCaptor.getValue().getStatus()).isEqualTo("ENABLED");

        ArgumentCaptor<SkillVersionEntity> versionCaptor = ArgumentCaptor.forClass(SkillVersionEntity.class);
        verify(skillVersionMapper).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getSkillId()).isEqualTo(101L);
        assertThat(versionCaptor.getValue().getVersion()).isEqualTo("1.0.0");
        assertThat(versionCaptor.getValue().getParameterSchema().path("properties").has("text")).isTrue();
        assertThat(versionCaptor.getValue().getRuntimeConfig().path("handlerClass").asText())
                .isEqualTo("com.example.EchoSkill");
        assertThat(versionCaptor.getValue().getStatus()).isEqualTo("READY");

        ArgumentCaptor<SkillArtifactEntity> artifactCaptor = ArgumentCaptor.forClass(SkillArtifactEntity.class);
        verify(skillArtifactMapper).insert(artifactCaptor.capture());
        assertThat(artifactCaptor.getValue().getSkillVersionId()).isEqualTo(201L);
        assertThat(artifactCaptor.getValue().getArtifactType()).isEqualTo("JAR");
        assertThat(artifactCaptor.getValue().getStoragePath()).isEqualTo("plugins/jar-echo.jar");
        assertThat(artifactCaptor.getValue().getFileName()).isEqualTo("jar-echo.jar");
        assertThat(artifactCaptor.getValue().getSizeBytes()).isEqualTo(4096L);
        assertThat(artifactCaptor.getValue().getChecksum()).isEqualTo("sha256:abc");

        verify(skillMapper).updateById(any(SkillEntity.class));
    }

    @Test
    void registerJarSkillMarksSkillFailedWhenManifestMissesHandlerClass() {
        when(skillMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(skillMapper.insert(any(SkillEntity.class))).thenAnswer(invocation -> {
            SkillEntity entity = invocation.getArgument(0);
            entity.setId(102L);
            return 1;
        });
        when(skillVersionMapper.insert(any(SkillVersionEntity.class))).thenAnswer(invocation -> {
            SkillVersionEntity entity = invocation.getArgument(0);
            entity.setId(202L);
            return 1;
        });

        var result = service.register(command("""
                {
                  "code": "jar_broken",
                  "name": "Broken Jar",
                  "version": "1.0.0",
                  "parameterSchema": {"type":"object"}
                }
                """));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.versionStatus()).isEqualTo("FAILED");
        assertThat(result.validationMessage()).contains("handlerClass");

        ArgumentCaptor<SkillEntity> skillCaptor = ArgumentCaptor.forClass(SkillEntity.class);
        verify(skillMapper).insert(skillCaptor.capture());
        assertThat(skillCaptor.getValue().getStatus()).isEqualTo("FAILED");

        ArgumentCaptor<SkillVersionEntity> versionCaptor = ArgumentCaptor.forClass(SkillVersionEntity.class);
        verify(skillVersionMapper).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(versionCaptor.getValue().getValidationResult().path("valid").asBoolean()).isFalse();

        verify(skillArtifactMapper, never()).insert(any(SkillArtifactEntity.class));
    }

    @Test
    void registerJarSkillLoadsHandlerFromJarAndMakesItExecutable() throws Exception {
        JarSkillRuntimeRegistry runtimeRegistry = new JarSkillRuntimeRegistry();
        DefaultJarSkillService loadingService = new DefaultJarSkillService(
                skillMapper,
                skillVersionMapper,
                skillArtifactMapper,
                objectMapper,
                new JarSkillLoader(runtimeRegistry)
        );
        when(skillMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(skillMapper.insert(any(SkillEntity.class))).thenAnswer(invocation -> {
            SkillEntity entity = invocation.getArgument(0);
            entity.setId(103L);
            return 1;
        });
        when(skillVersionMapper.insert(any(SkillVersionEntity.class))).thenAnswer(invocation -> {
            SkillVersionEntity entity = invocation.getArgument(0);
            entity.setId(203L);
            return 1;
        });
        Path jarPath = compileEchoSkillJar();

        var result = loadingService.register(new RegisterJarSkillCommand(
                1L,
                10001L,
                "PERSONAL",
                jarPath,
                "jar-echo.jar",
                Files.size(jarPath),
                "sha256:jar",
                validManifest()
        ));

        assertThat(result.status()).isEqualTo("ENABLED");
        assertThat(result.versionStatus()).isEqualTo("READY");
        var handler = runtimeRegistry.find("jar_echo");
        try {
            assertThat(handler).isPresent();
            var output = handler.get().execute(new SkillExecuteCommand(
                    1L,
                    10001L,
                    "jar_echo",
                    objectMapper.readTree("{\"text\":\"hello\"}")
            ));
            assertThat(output.success()).isTrue();
            assertThat(output.output().path("echo").asText()).isEqualTo("hello");
        } finally {
            runtimeRegistry.closeAll();
        }
    }

    @Test
    void registerJarSkillMarksVersionFailedWhenHandlerClassCannotBeLoaded() throws Exception {
        JarSkillRuntimeRegistry runtimeRegistry = new JarSkillRuntimeRegistry();
        DefaultJarSkillService loadingService = new DefaultJarSkillService(
                skillMapper,
                skillVersionMapper,
                skillArtifactMapper,
                objectMapper,
                new JarSkillLoader(runtimeRegistry)
        );
        when(skillMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(skillMapper.insert(any(SkillEntity.class))).thenAnswer(invocation -> {
            SkillEntity entity = invocation.getArgument(0);
            entity.setId(104L);
            return 1;
        });
        when(skillVersionMapper.insert(any(SkillVersionEntity.class))).thenAnswer(invocation -> {
            SkillVersionEntity entity = invocation.getArgument(0);
            entity.setId(204L);
            return 1;
        });
        Path jarPath = compileEchoSkillJar();

        var result = loadingService.register(new RegisterJarSkillCommand(
                1L,
                10001L,
                "PERSONAL",
                jarPath,
                "jar-echo.jar",
                Files.size(jarPath),
                "sha256:jar",
                validManifest().replace("com.example.EchoSkill", "com.example.MissingSkill")
        ));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.versionStatus()).isEqualTo("FAILED");
        assertThat(result.validationMessage()).contains("load failed");
        assertThat(runtimeRegistry.find("jar_echo")).isEmpty();

        ArgumentCaptor<SkillVersionEntity> versionUpdateCaptor = ArgumentCaptor.forClass(SkillVersionEntity.class);
        verify(skillVersionMapper).updateById(versionUpdateCaptor.capture());
        assertThat(versionUpdateCaptor.getValue().getId()).isEqualTo(204L);
        assertThat(versionUpdateCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(versionUpdateCaptor.getValue().getValidationResult().path("valid").asBoolean()).isFalse();

        ArgumentCaptor<SkillEntity> skillUpdateCaptor = ArgumentCaptor.forClass(SkillEntity.class);
        verify(skillMapper).updateById(skillUpdateCaptor.capture());
        assertThat(skillUpdateCaptor.getValue().getId()).isEqualTo(104L);
        assertThat(skillUpdateCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(skillUpdateCaptor.getValue().getCurrentVersionId()).isNull();
        runtimeRegistry.closeAll();
    }

    private RegisterJarSkillCommand command(String manifestJson) {
        return new RegisterJarSkillCommand(
                1L,
                10001L,
                "PERSONAL",
                Path.of("plugins/jar-echo.jar"),
                "jar-echo.jar",
                4096L,
                "sha256:abc",
                manifestJson
        );
    }

    private String validManifest() {
        return """
                {
                  "code": "jar_echo",
                  "name": "Jar Echo",
                  "description": "Echo from jar",
                  "version": "1.0.0",
                  "handlerClass": "com.example.EchoSkill",
                  "parameterSchema": {
                    "type": "object",
                    "properties": {"text": {"type": "string"}}
                  },
                  "returnSchema": {"type": "object"},
                  "permissions": {"network": false},
                  "dependencies": []
                }
                """;
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
