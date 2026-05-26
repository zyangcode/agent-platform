package com.ls.agent.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.core.profile.api.ProfileService;
import com.ls.agent.core.profile.command.BindSkillsCommand;
import com.ls.agent.core.profile.command.CreateProfileCommand;
import com.ls.agent.core.profile.command.UpdateProfileCommand;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.web.dto.BindMcpToolsRequest;
import com.ls.agent.web.dto.BindSkillsRequest;
import com.ls.agent.web.dto.CreateProfileRequest;
import com.ls.agent.web.dto.UpdateProfileRequest;
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
        controllers = ProfileController.class,
        properties = {
                "security.jwt.secret=test-secret-test-secret-test-secret-test",
                "security.jwt.expires-in-seconds=7200"
        }
)
@Import(WebMvcTestSupport.class)
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private ProfileService profileService;

    @Test
    void createProfileDelegatesWithCurrentUser() throws Exception {
        when(profileService.createProfile(any(CreateProfileCommand.class))).thenReturn(profile());

        mockMvc.perform(post("/api/profiles")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProfileRequest(
                                20001L,
                                "General Assistant",
                                "GENERAL",
                                "Stage 1 assistant",
                                1L,
                                "Be concise.",
                                objectMapper.createObjectNode().put("shortTermEnabled", true),
                                5,
                                "PRIVATE"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileId").value(50001))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        ArgumentCaptor<CreateProfileCommand> captor = ArgumentCaptor.forClass(CreateProfileCommand.class);
        verify(profileService).createProfile(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().ownerUserId()).isEqualTo(10001L);
        assertThat(captor.getValue().applicationId()).isEqualTo(20001L);
    }

    @Test
    void listProfilesDelegatesWithCurrentUserAndApplicationId() throws Exception {
        when(profileService.pageProfiles(1L, 10001L, 20001L, 2, 20))
                .thenReturn(PageResult.of(List.of(profile()), 2, 20, 1));

        mockMvc.perform(get("/api/profiles")
                        .header("Authorization", bearerToken())
                        .param("applicationId", "20001")
                        .param("pageNo", "2")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].profileId").value(50001));
    }

    @Test
    void getProfileDelegatesWithCurrentUser() throws Exception {
        when(profileService.getProfile(1L, 10001L, 50001L)).thenReturn(profile());

        mockMvc.perform(get("/api/profiles/50001")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("General Assistant"));
    }

    @Test
    void updateProfileDelegatesWithCurrentUser() throws Exception {
        when(profileService.updateProfile(any(UpdateProfileCommand.class))).thenReturn(profile());

        mockMvc.perform(put("/api/profiles/50001")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest(
                                "Updated Assistant",
                                "Updated description",
                                2L,
                                "Updated prompt.",
                                objectMapper.createObjectNode().put("mode", "READ_ONLY"),
                                3,
                                "PRIVATE"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileId").value(50001));

        ArgumentCaptor<UpdateProfileCommand> captor = ArgumentCaptor.forClass(UpdateProfileCommand.class);
        verify(profileService).updateProfile(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().ownerUserId()).isEqualTo(10001L);
        assertThat(captor.getValue().profileId()).isEqualTo(50001L);
        assertThat(captor.getValue().modelConfigId()).isEqualTo(2L);
    }

    @Test
    void updateProfileReturnsInternalErrorWhenServiceFails() throws Exception {
        when(profileService.updateProfile(any(UpdateProfileCommand.class))).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(put("/api/profiles/50001")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest(
                                "Updated Assistant",
                                "Updated description",
                                2L,
                                "Updated prompt.",
                                objectMapper.createObjectNode().put("mode", "READ_ONLY"),
                                3,
                                "PRIVATE"
                        ))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR.getCode()));
    }

    @Test
    void disableProfileDelegatesWithCurrentUser() throws Exception {
        when(profileService.disableProfile(1L, 10001L, 50001L)).thenReturn(disabledProfile());

        mockMvc.perform(post("/api/profiles/50001/disable")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileId").value(50001))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        verify(profileService).disableProfile(1L, 10001L, 50001L);
    }

    @Test
    void bindSkillsDelegatesWithCurrentUser() throws Exception {
        mockMvc.perform(put("/api/profiles/50001/skills")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BindSkillsRequest(List.of(1L, 2L)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        ArgumentCaptor<BindSkillsCommand> captor = ArgumentCaptor.forClass(BindSkillsCommand.class);
        verify(profileService).bindSkills(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().ownerUserId()).isEqualTo(10001L);
        assertThat(captor.getValue().profileId()).isEqualTo(50001L);
        assertThat(captor.getValue().skillIds()).containsExactly(1L, 2L);
    }

    @Test
    void bindMcpToolsDelegatesWithCurrentUser() throws Exception {
        mockMvc.perform(put("/api/profiles/50001/mcp-tools")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BindMcpToolsRequest(List.of(1L)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    private ProfileDTO profile() {
        return new ProfileDTO(
                50001L,
                20001L,
                "General Assistant",
                "GENERAL",
                "Stage 1 assistant",
                1L,
                "Be concise.",
                objectMapper.createObjectNode().put("shortTermEnabled", true),
                5,
                "PRIVATE",
                "DRAFT",
                List.of(),
                List.of()
        );
    }

    private ProfileDTO disabledProfile() {
        ProfileDTO profile = profile();
        return new ProfileDTO(
                profile.profileId(),
                profile.applicationId(),
                profile.name(),
                profile.profileType(),
                profile.description(),
                profile.modelConfigId(),
                profile.promptExtra(),
                profile.memoryStrategy(),
                profile.maxSteps(),
                profile.visibility(),
                "DISABLED",
                profile.skillBindings(),
                profile.mcpToolBindings()
        );
    }

    private String bearerToken() {
        CurrentUserDTO user = new CurrentUserDTO(10001L, 1L, "alice", "Alice", List.of("USER"));
        return "Bearer " + jwtTokenService.generate(user);
    }
}
