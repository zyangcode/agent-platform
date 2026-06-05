package com.ls.agent.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.core.skill.api.JarSkillService;
import com.ls.agent.core.skill.api.SkillQueryService;
import com.ls.agent.core.skill.command.RegisterJarSkillCommand;
import com.ls.agent.core.skill.dto.JarSkillRegistrationResult;
import com.ls.agent.core.skill.dto.SkillDTO;
import com.ls.agent.web.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SkillController.class,
        properties = {
                "security.jwt.secret=test-secret-test-secret-test-secret-test",
                "security.jwt.expires-in-seconds=7200"
        }
)
@Import(WebMvcTestSupport.class)
@TestPropertySource(properties = "skill.jar.storage-dir=target/test-jar-skills")
class SkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private SkillQueryService skillQueryService;

    @MockBean
    private JarSkillService jarSkillService;

    @Test
    void authenticatedUserCanListSkills() throws Exception {
        when(skillQueryService.listSkills(1L, "GLOBAL", "ENABLED"))
                .thenReturn(List.of(new SkillDTO(
                        1L,
                        "calculator",
                        "Calculator",
                        "Built-in arithmetic calculator",
                        "BUILTIN",
                        "GLOBAL",
                        "ENABLED",
                        objectMapper.createObjectNode().put("type", "object")
                )));

        mockMvc.perform(get("/api/skills")
                        .header("Authorization", bearerToken())
                        .param("scope", "GLOBAL")
                        .param("status", "ENABLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].skillId").value(1))
                .andExpect(jsonPath("$.data[0].code").value("calculator"));
    }

    @Test
    void uploadJarSkillStoresJarAndDelegatesRegistrationWithCurrentUser() throws Exception {
        when(jarSkillService.register(any(RegisterJarSkillCommand.class)))
                .thenReturn(new JarSkillRegistrationResult(
                        101L,
                        201L,
                        "jar_echo",
                        "ENABLED",
                        "READY",
                        "OK"
                ));
        MockMultipartFile jarFile = new MockMultipartFile(
                "jarFile",
                "jar-echo.jar",
                "application/java-archive",
                "fake-jar".getBytes()
        );
        MockMultipartFile manifest = new MockMultipartFile(
                "manifest",
                "",
                "application/json",
                """
                        {"code":"jar_echo","handlerClass":"com.example.EchoSkill","parameterSchema":{"type":"object"}}
                        """.getBytes()
        );

        mockMvc.perform(multipart("/api/skills/jar")
                        .file(jarFile)
                        .file(manifest)
                        .header("Authorization", bearerToken())
                        .param("scope", "PERSONAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillId").value(101))
                .andExpect(jsonPath("$.data.skillCode").value("jar_echo"))
                .andExpect(jsonPath("$.data.versionStatus").value("READY"));

        ArgumentCaptor<RegisterJarSkillCommand> captor = ArgumentCaptor.forClass(RegisterJarSkillCommand.class);
        verify(jarSkillService).register(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().ownerUserId()).isEqualTo(10001L);
        assertThat(captor.getValue().scope()).isEqualTo("PERSONAL");
        assertThat(captor.getValue().fileName()).isEqualTo("jar-echo.jar");
        assertThat(captor.getValue().sizeBytes()).isEqualTo(8L);
        assertThat(captor.getValue().checksum()).startsWith("sha256:");
        assertThat(captor.getValue().manifestJson()).contains("jar_echo");
        assertThat(captor.getValue().jarPath()).exists();
    }

    private String bearerToken() {
        CurrentUserDTO user = new CurrentUserDTO(10001L, 1L, "alice", "Alice", List.of("USER"));
        return "Bearer " + jwtTokenService.generate(user);
    }
}
