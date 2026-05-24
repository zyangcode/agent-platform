package com.ls.agent.web.controller;

import com.ls.agent.core.identity.api.ApiKeyGenerator;
import com.ls.agent.core.identity.api.AuthService;
import com.ls.agent.core.identity.api.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        properties = {
                "security.jwt.secret=test-secret-test-secret-test-secret-test",
                "security.jwt.expires-in-seconds=7200"
        }
)
@Import(WebMvcTestSupport.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private PasswordHasher passwordHasher;

    @MockBean
    private ApiKeyGenerator apiKeyGenerator;

    @Test
    void swaggerDocsAreAccessibleWithoutJwt() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isNotFound());
    }
}
