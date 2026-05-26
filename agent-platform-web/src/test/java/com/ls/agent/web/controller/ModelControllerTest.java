package com.ls.agent.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.model.api.ModelConfigService;
import com.ls.agent.core.model.command.CreateModelConfigCommand;
import com.ls.agent.core.model.command.CreateModelProviderCommand;
import com.ls.agent.core.model.dto.ModelConfigDTO;
import com.ls.agent.core.model.dto.ModelProviderDTO;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.web.dto.CreateModelConfigRequest;
import com.ls.agent.web.dto.CreateModelProviderRequest;
import com.ls.agent.web.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ModelController.class,
        properties = {
                "security.jwt.secret=test-secret-test-secret-test-secret-test",
                "security.jwt.expires-in-seconds=7200"
        }
)
@Import(WebMvcTestSupport.class)
class ModelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private ModelConfigService modelConfigService;

    @Test
    void userCannotCreateModelProvider() throws Exception {
        mockMvc.perform(post("/api/admin/model-providers")
                        .header("Authorization", bearerToken("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateModelProviderRequest(
                                "OpenAI",
                                "OPENAI_COMPATIBLE",
                                "https://api.openai.com/v1",
                                "sk-live"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateModelProvider() throws Exception {
        when(modelConfigService.createProvider(any(CreateModelProviderCommand.class)))
                .thenReturn(new ModelProviderDTO(1L, "OpenAI", "OPENAI_COMPATIBLE", "https://api.openai.com/v1", "ACTIVE"));

        mockMvc.perform(post("/api/admin/model-providers")
                        .header("Authorization", bearerToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateModelProviderRequest(
                                "OpenAI",
                                "OPENAI_COMPATIBLE",
                                "https://api.openai.com/v1",
                                "sk-live"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerId").value(1))
                .andExpect(jsonPath("$.data.name").value("OpenAI"));

        ArgumentCaptor<CreateModelProviderCommand> captor = ArgumentCaptor.forClass(CreateModelProviderCommand.class);
        verify(modelConfigService).createProvider(captor.capture());
        assertThat(captor.getValue().apiKey()).isEqualTo("sk-live");
    }

    @Test
    void adminCanCreateModelConfig() throws Exception {
        when(modelConfigService.createModelConfig(any(CreateModelConfigCommand.class)))
                .thenReturn(new ModelConfigDTO(
                        10L,
                        1L,
                        "gpt-4o-mini",
                        "GPT 4o Mini",
                        objectMapper.createObjectNode().put("text", true),
                        new BigDecimal("0.70"),
                        128000,
                        "ACTIVE"
                ));

        mockMvc.perform(post("/api/admin/model-configs")
                        .header("Authorization", bearerToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateModelConfigRequest(
                                1L,
                                "gpt-4o-mini",
                                "GPT 4o Mini",
                                "{\"text\":true}",
                                new BigDecimal("0.70"),
                                128000
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelConfigId").value(10))
                .andExpect(jsonPath("$.data.modelName").value("gpt-4o-mini"));
    }

    @Test
    void authenticatedUserCanListModelConfigs() throws Exception {
        when(modelConfigService.listActiveModelConfigs())
                .thenReturn(List.of(new ModelConfigDTO(
                        1L,
                        1L,
                        "mock-chat",
                        "Mock Chat Model",
                        objectMapper.createObjectNode().put("text", true),
                        new BigDecimal("0.70"),
                        8192,
                        "ACTIVE"
                )));

        mockMvc.perform(get("/api/model-configs")
                        .header("Authorization", bearerToken("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].modelConfigId").value(1))
                .andExpect(jsonPath("$.data[0].modelName").value("mock-chat"));
    }

    @Test
    void adminCanListModelProviders() throws Exception {
        when(modelConfigService.listActiveProviders())
                .thenReturn(List.of(new ModelProviderDTO(
                        1L,
                        "OpenAI",
                        "OPENAI_COMPATIBLE",
                        "https://api.openai.com/v1",
                        "ACTIVE"
                )));

        mockMvc.perform(get("/api/admin/model-providers")
                        .header("Authorization", bearerToken("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].providerId").value(1))
                .andExpect(jsonPath("$.data[0].providerType").value("OPENAI_COMPATIBLE"));
    }

    private String bearerToken(String role) {
        CurrentUserDTO user = new CurrentUserDTO(10001L, 1L, "alice", "Alice", List.of(role));
        return "Bearer " + jwtTokenService.generate(user);
    }
}
