package com.ls.agent.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.core.mcp.api.McpServerService;
import com.ls.agent.core.mcp.command.CreateMcpServerCommand;
import com.ls.agent.core.mcp.dto.McpServerDTO;
import com.ls.agent.web.dto.CreateMcpServerRequest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = McpServerController.class,
        properties = {
                "security.jwt.secret=test-secret-test-secret-test-secret-test",
                "security.jwt.expires-in-seconds=7200"
        }
)
@Import(WebMvcTestSupport.class)
class McpServerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private McpServerService mcpServerService;

    @Test
    void authenticatedUserCanListTenantMcpServers() throws Exception {
        when(mcpServerService.list(1L, "ACTIVE")).thenReturn(List.of(server()));

        mockMvc.perform(get("/api/mcp-servers")
                        .header("Authorization", bearerToken())
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].mcpServerId").value(10))
                .andExpect(jsonPath("$.data[0].name").value("Readonly Filesystem MCP"));
    }

    @Test
    void createMcpServerDelegatesWithCurrentTenant() throws Exception {
        when(mcpServerService.create(any(CreateMcpServerCommand.class))).thenReturn(server());

        mockMvc.perform(post("/api/mcp-servers")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateMcpServerRequest(
                                "Readonly Filesystem MCP",
                                "STDIO",
                                objectMapper.createObjectNode().put("command", "builtin-demo-filesystem-mcp")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mcpServerId").value(10));

        ArgumentCaptor<CreateMcpServerCommand> captor = ArgumentCaptor.forClass(CreateMcpServerCommand.class);
        verify(mcpServerService).create(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().connectionConfig().get("command").asText()).isEqualTo("builtin-demo-filesystem-mcp");
    }

    @Test
    void disableMcpServerDelegatesWithCurrentTenant() throws Exception {
        McpServerDTO disabled = new McpServerDTO(
                10L,
                "Readonly Filesystem MCP",
                "STDIO",
                objectMapper.createObjectNode().put("command", "builtin-demo-filesystem-mcp"),
                "DISABLED"
        );
        when(mcpServerService.disable(1L, 10L)).thenReturn(disabled);

        mockMvc.perform(post("/api/mcp-servers/10/disable")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    private McpServerDTO server() {
        return new McpServerDTO(
                10L,
                "Readonly Filesystem MCP",
                "STDIO",
                objectMapper.createObjectNode().put("command", "builtin-demo-filesystem-mcp"),
                "ACTIVE"
        );
    }

    private String bearerToken() {
        CurrentUserDTO user = new CurrentUserDTO(10001L, 1L, "alice", "Alice", List.of("USER"));
        return "Bearer " + jwtTokenService.generate(user);
    }
}
