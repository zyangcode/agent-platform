package com.ls.agent.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.core.skill.api.SkillQueryService;
import com.ls.agent.core.skill.dto.SkillDTO;
import com.ls.agent.web.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class SkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private SkillQueryService skillQueryService;

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

    private String bearerToken() {
        CurrentUserDTO user = new CurrentUserDTO(10001L, 1L, "alice", "Alice", List.of("USER"));
        return "Bearer " + jwtTokenService.generate(user);
    }
}
