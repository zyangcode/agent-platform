package com.ls.agent.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.identity.api.ApiKeyGenerator;
import com.ls.agent.core.identity.api.AuthService;
import com.ls.agent.core.identity.api.PasswordHasher;
import com.ls.agent.core.identity.command.LoginCommand;
import com.ls.agent.core.identity.command.RegisterCommand;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.core.identity.dto.LoginResult;
import com.ls.agent.core.identity.dto.RegisterResult;
import com.ls.agent.web.dto.LoginRequest;
import com.ls.agent.web.dto.RegisterRequest;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        properties = {
                "security.jwt.secret=test-secret-test-secret-test-secret-test",
                "security.jwt.expires-in-seconds=7200"
        }
)
@Import(WebMvcTestSupport.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private AuthService authService;

    @MockBean
    private PasswordHasher passwordHasher;

    @MockBean
    private ApiKeyGenerator apiKeyGenerator;

    @Test
    void registerDelegatesToAuthService() throws Exception {
        when(authService.register(any(RegisterCommand.class)))
                .thenReturn(new RegisterResult(10001L, 1L, "alice", "Alice"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("alice", "password123", "Alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(10001));

        ArgumentCaptor<RegisterCommand> captor = ArgumentCaptor.forClass(RegisterCommand.class);
        org.mockito.Mockito.verify(authService).register(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("alice");
    }

    @Test
    void loginReturnsJwtToken() throws Exception {
        CurrentUserDTO user = new CurrentUserDTO(10001L, 1L, "alice", "Alice", List.of("USER"));
        when(authService.login(any(LoginCommand.class))).thenReturn(new LoginResult(user));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("alice", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.user.username").value("alice"));
    }

    @Test
    void meRequiresValidJwt() throws Exception {
        CurrentUserDTO user = new CurrentUserDTO(10001L, 1L, "alice", "Alice", List.of("USER"));
        when(authService.getCurrentUser(10001L)).thenReturn(user);
        String token = jwtTokenService.generate(user);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(10001))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));
    }
}
