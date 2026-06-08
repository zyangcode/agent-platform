package com.ls.agent.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.core.memory.api.MemoryManagementService;
import com.ls.agent.core.memory.command.UpdateMemoryCommand;
import com.ls.agent.core.memory.dto.MemoryRecordDTO;
import com.ls.agent.web.dto.UpdateMemoryRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = MemoryController.class,
        properties = {
                "security.jwt.secret=test-secret-test-secret-test-secret-test",
                "security.jwt.expires-in-seconds=7200"
        }
)
@Import(WebMvcTestSupport.class)
class MemoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private MemoryManagementService memoryManagementService;

    @Test
    void listMemoriesDelegatesWithCurrentUserScope() throws Exception {
        when(memoryManagementService.list(1L, 20001L, 10001L, 50001L, "preference", "basketball", 20))
                .thenReturn(List.of(memory()));

        mockMvc.perform(get("/api/memories")
                        .header("Authorization", bearerToken())
                        .param("applicationId", "20001")
                        .param("profileId", "50001")
                        .param("category", "preference")
                        .param("query", "basketball")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(88))
                .andExpect(jsonPath("$.data[0].content").value("User likes evening basketball."));
    }

    @Test
    void updateMemoryDelegatesWithCurrentUserScope() throws Exception {
        when(memoryManagementService.update(any(UpdateMemoryCommand.class))).thenReturn(memory());

        mockMvc.perform(patch("/api/memories/88")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateMemoryRequest(
                                20001L,
                                50001L,
                                "User prefers concise basketball advice.",
                                "preference",
                                List.of("sports", "style"),
                                0.9,
                                "preference",
                                true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(88));

        ArgumentCaptor<UpdateMemoryCommand> captor = ArgumentCaptor.forClass(UpdateMemoryCommand.class);
        verify(memoryManagementService).update(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().userId()).isEqualTo(10001L);
        assertThat(captor.getValue().applicationId()).isEqualTo(20001L);
        assertThat(captor.getValue().profileId()).isEqualTo(50001L);
        assertThat(captor.getValue().memoryId()).isEqualTo(88L);
        assertThat(captor.getValue().content()).isEqualTo("User prefers concise basketball advice.");
        assertThat(captor.getValue().pinned()).isTrue();
    }

    @Test
    void deleteMemoryDisablesMemoryWithCurrentUserScope() throws Exception {
        when(memoryManagementService.disable(1L, 20001L, 10001L, 50001L, 88L)).thenReturn(1);

        mockMvc.perform(delete("/api/memories/88")
                        .header("Authorization", bearerToken())
                        .param("applicationId", "20001")
                        .param("profileId", "50001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
    }

    private MemoryRecordDTO memory() {
        return new MemoryRecordDTO(
                88L,
                20001L,
                50001L,
                "PREFERENCE",
                "preference",
                "User likes evening basketball.",
                List.of("sports"),
                0.8,
                0.9,
                2,
                LocalDateTime.parse("2026-06-01T10:00:00"),
                LocalDateTime.parse("2026-06-01T09:00:00"),
                LocalDateTime.parse("2026-06-01T10:00:00"),
                "preference",
                "ACTIVE",
                true
        );
    }

    private String bearerToken() {
        CurrentUserDTO user = new CurrentUserDTO(10001L, 1L, "alice", "Alice", List.of("USER"));
        return "Bearer " + jwtTokenService.generate(user);
    }
}
