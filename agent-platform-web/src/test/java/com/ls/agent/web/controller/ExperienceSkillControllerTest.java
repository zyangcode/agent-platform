package com.ls.agent.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.experience.api.ExperienceSkillService;
import com.ls.agent.core.experience.command.CreateExperienceSkillCommand;
import com.ls.agent.core.experience.command.UpdateExperienceSkillCommand;
import com.ls.agent.core.experience.dto.ExperienceSkillDTO;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.web.dto.CreateExperienceSkillRequest;
import com.ls.agent.web.dto.UpdateExperienceSkillRequest;
import com.ls.agent.web.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ExperienceSkillController.class,
        properties = {
                "security.jwt.secret=test-secret-test-secret-test-secret-test",
                "security.jwt.expires-in-seconds=7200"
        }
)
@Import(WebMvcTestSupport.class)
class ExperienceSkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private ExperienceSkillService experienceSkillService;

    @Test
    void createExperienceSkillDelegatesWithCurrentUser() throws Exception {
        when(experienceSkillService.create(any(CreateExperienceSkillCommand.class))).thenReturn(experienceSkill());

        mockMvc.perform(post("/api/experience-skills")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateExperienceSkillRequest(
                                20001L,
                                50001L,
                                "support-refund-tone",
                                "Support refund tone",
                                "SUPPORT",
                                List.of("refund", "tone"),
                                "Use empathy first."
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.experienceSkillId").value(70001))
                .andExpect(jsonPath("$.data.code").value("support-refund-tone"));

        ArgumentCaptor<CreateExperienceSkillCommand> captor = ArgumentCaptor.forClass(CreateExperienceSkillCommand.class);
        verify(experienceSkillService).create(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().ownerUserId()).isEqualTo(10001L);
        assertThat(captor.getValue().applicationId()).isEqualTo(20001L);
        assertThat(captor.getValue().triggerKeywords()).containsExactly("refund", "tone");
    }

    @Test
    void listExperienceSkillsDelegatesWithCurrentUserAndApplication() throws Exception {
        when(experienceSkillService.page(1L, 10001L, 20001L, 1, 20))
                .thenReturn(PageResult.of(List.of(experienceSkill()), 1, 20, 1));

        mockMvc.perform(get("/api/experience-skills")
                        .header("Authorization", bearerToken())
                        .param("applicationId", "20001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].code").value("support-refund-tone"));
    }

    @Test
    void listExperienceSkillsNormalizesInvalidPaginationAtWebBoundary() throws Exception {
        when(experienceSkillService.page(1L, 10001L, 20001L, 1, 20))
                .thenReturn(PageResult.empty(1, 20));

        mockMvc.perform(get("/api/experience-skills")
                        .header("Authorization", bearerToken())
                        .param("applicationId", "20001")
                        .param("pageNo", "0")
                        .param("pageSize", "0"))
                .andExpect(status().isOk());

        verify(experienceSkillService).page(1L, 10001L, 20001L, 1, 20);
    }

    @Test
    void updateExperienceSkillDelegatesWithCurrentUser() throws Exception {
        when(experienceSkillService.update(any(UpdateExperienceSkillCommand.class))).thenReturn(experienceSkill());

        mockMvc.perform(put("/api/experience-skills/70001")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateExperienceSkillRequest(
                                20001L,
                                "support-refund-tone",
                                "Support refund tone",
                                "SUPPORT",
                                List.of("refund"),
                                "Updated"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.experienceSkillId").value(70001));

        ArgumentCaptor<UpdateExperienceSkillCommand> captor = ArgumentCaptor.forClass(UpdateExperienceSkillCommand.class);
        verify(experienceSkillService).update(captor.capture());
        assertThat(captor.getValue().experienceSkillId()).isEqualTo(70001L);
        assertThat(captor.getValue().ownerUserId()).isEqualTo(10001L);
    }

    @Test
    void disableExperienceSkillDelegatesWithCurrentUser() throws Exception {
        when(experienceSkillService.disable(1L, 10001L, 20001L, 70001L)).thenReturn(experienceSkill());

        mockMvc.perform(post("/api/experience-skills/70001/disable")
                        .header("Authorization", bearerToken())
                        .param("applicationId", "20001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.experienceSkillId").value(70001));
    }

    private ExperienceSkillDTO experienceSkill() {
        return new ExperienceSkillDTO(
                70001L,
                "support-refund-tone",
                "Support refund tone",
                "SUPPORT",
                List.of(),
                "Use empathy first."
        );
    }

    private String bearerToken() {
        CurrentUserDTO user = new CurrentUserDTO(10001L, 1L, "alice", "Alice", List.of("USER"));
        return "Bearer " + jwtTokenService.generate(user);
    }
}
