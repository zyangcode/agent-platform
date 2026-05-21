package com.ls.agent.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.core.mcp.api.McpToolQueryService;
import com.ls.agent.core.mcp.dto.McpToolDTO;
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
        controllers = McpToolController.class,
        properties = {
                "security.jwt.secret=test-secret-test-secret-test-secret-test",
                "security.jwt.expires-in-seconds=7200"
        }
)
@Import(WebMvcTestSupport.class)
class McpToolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private McpToolQueryService mcpToolQueryService;

    @Test
    void authenticatedUserCanListMcpTools() throws Exception {
        when(mcpToolQueryService.listTools(1L, "AVAILABLE"))
                .thenReturn(List.of(new McpToolDTO(
                        1L,
                        10L,
                        "read_file",
                        "Read an allowed local text file in readonly mode",
                        "AVAILABLE",
                        objectMapper.createObjectNode().put("type", "object")
                )));

        mockMvc.perform(get("/api/mcp-tools")
                        .header("Authorization", bearerToken())
                        .param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].mcpToolId").value(1))
                .andExpect(jsonPath("$.data[0].name").value("read_file"));
    }

    private String bearerToken() {
        CurrentUserDTO user = new CurrentUserDTO(10001L, 1L, "alice", "Alice", List.of("USER"));
        return "Bearer " + jwtTokenService.generate(user);
    }
}
