package com.ls.agent.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.identity.api.ApplicationService;
import com.ls.agent.core.identity.command.CreateApplicationCommand;
import com.ls.agent.core.identity.dto.ApiKeyDTO;
import com.ls.agent.core.identity.dto.ApplicationDTO;
import com.ls.agent.core.identity.dto.CreatedApiKeyDTO;
import com.ls.agent.core.identity.dto.CreateApplicationResult;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.core.identity.dto.RevokeApiKeyResult;
import com.ls.agent.web.dto.CreateApplicationRequest;
import com.ls.agent.web.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ApplicationController.class,
        properties = {
                "security.jwt.secret=test-secret-test-secret-test-secret-test",
                "security.jwt.expires-in-seconds=7200"
        }
)
@Import(WebMvcTestSupport.class)
class ApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private ApplicationService applicationService;

    @Test
    void createApplicationReturnsPlaintextApiKeyOnce() throws Exception {
        when(applicationService.createApplication(org.mockito.ArgumentMatchers.any(CreateApplicationCommand.class)))
                .thenReturn(new CreateApplicationResult(
                        20001L,
                        "Demo App",
                        "ACTIVE",
                        new CreatedApiKeyDTO(30001L, "default", "ak_live_plaintext", "ak_live", "ACTIVE")
                ));

        mockMvc.perform(post("/api/applications")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateApplicationRequest(
                                "Demo App",
                                "stage 1 test application"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationId").value(20001))
                .andExpect(jsonPath("$.data.apiKey.key").value("ak_live_plaintext"))
                .andExpect(jsonPath("$.data.apiKey.keyPrefix").value("ak_live"));

        ArgumentCaptor<CreateApplicationCommand> captor = ArgumentCaptor.forClass(CreateApplicationCommand.class);
        verify(applicationService).createApplication(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().ownerUserId()).isEqualTo(10001L);
        assertThat(captor.getValue().name()).isEqualTo("Demo App");
    }

    @Test
    void listApplicationsCapsPageSizeAtOneHundred() throws Exception {
        when(applicationService.pageApplications(1L, 10001L, 2, 100))
                .thenReturn(PageResult.of(List.of(new ApplicationDTO(
                        20001L,
                        "Demo App",
                        "stage 1 test application",
                        "ACTIVE",
                        LocalDateTime.of(2026, 5, 21, 10, 0)
                )), 2, 100, 101));

        mockMvc.perform(get("/api/applications")
                        .header("Authorization", bearerToken())
                        .param("pageNo", "2")
                        .param("pageSize", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(100))
                .andExpect(jsonPath("$.data.records[0].applicationId").value(20001))
                .andExpect(jsonPath("$.data.records[0].name").value("Demo App"));

        verify(applicationService).pageApplications(1L, 10001L, 2, 100);
    }

    @Test
    void listApiKeysDelegatesWithCurrentUserAndApplicationId() throws Exception {
        when(applicationService.listApiKeys(1L, 10001L, 20001L))
                .thenReturn(List.of(new ApiKeyDTO(
                        30001L,
                        "default",
                        "ak_live",
                        "ACTIVE",
                        null,
                        LocalDateTime.of(2026, 5, 21, 10, 0)
                )));

        mockMvc.perform(get("/api/applications/20001/api-keys")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].apiKeyId").value(30001))
                .andExpect(jsonPath("$.data[0].keyPrefix").value("ak_live"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));

        verify(applicationService).listApiKeys(1L, 10001L, 20001L);
    }

    @Test
    void revokeApiKeyDelegatesWithCurrentUserApplicationIdAndApiKeyId() throws Exception {
        when(applicationService.revokeApiKey(1L, 10001L, 20001L, 30001L))
                .thenReturn(new RevokeApiKeyResult(30001L, "REVOKED"));

        mockMvc.perform(post("/api/applications/20001/api-keys/30001/revoke")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKeyId").value(30001))
                .andExpect(jsonPath("$.data.status").value("REVOKED"));

        verify(applicationService).revokeApiKey(1L, 10001L, 20001L, 30001L);
    }

    private String bearerToken() {
        CurrentUserDTO user = new CurrentUserDTO(10001L, 1L, "alice", "Alice", List.of("USER"));
        return "Bearer " + jwtTokenService.generate(user);
    }
}
