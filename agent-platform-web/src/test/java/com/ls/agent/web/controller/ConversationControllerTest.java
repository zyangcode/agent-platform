package com.ls.agent.web.controller;

import com.ls.agent.core.agent.api.MessageHistoryService;
import com.ls.agent.core.agent.dto.ConversationMessageDTO;
import com.ls.agent.core.agent.dto.ConversationSummaryDTO;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.web.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ConversationController.class,
        properties = {
                "security.jwt.secret=test-secret-test-secret-test-secret-test",
                "security.jwt.expires-in-seconds=7200"
        }
)
@Import(WebMvcTestSupport.class)
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private MessageHistoryService messageHistoryService;

    @Test
    void listDelegatesWithCurrentUserAndFilters() throws Exception {
        when(messageHistoryService.listConversations(1L, 20001L, 10001L, 50001L, 20))
                .thenReturn(List.of(new ConversationSummaryDTO(
                        90001L,
                        20001L,
                        50001L,
                        "New conversation",
                        "WEB",
                        "ACTIVE",
                        LocalDateTime.of(2026, 5, 26, 10, 0),
                        LocalDateTime.of(2026, 5, 26, 10, 5)
                )));

        mockMvc.perform(get("/api/conversations")
                        .header("Authorization", bearerToken())
                        .param("applicationId", "20001")
                        .param("profileId", "50001")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].conversationId").value(90001))
                .andExpect(jsonPath("$.data[0].title").value("New conversation"));
    }

    @Test
    void messagesDelegatesWithCurrentUserAndScope() throws Exception {
        when(messageHistoryService.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 50))
                .thenReturn(List.of(new ConversationMessageDTO(1L, "user", "hello", 1, "tr_1")));

        mockMvc.perform(get("/api/conversations/90001/messages")
                        .header("Authorization", bearerToken())
                        .param("applicationId", "20001")
                        .param("profileId", "50001")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].messageId").value(1))
                .andExpect(jsonPath("$.data[0].content").value("hello"));

        verify(messageHistoryService).listRecentMessages(
                1L,
                20001L,
                10001L,
                50001L,
                90001L,
                50
        );
    }

    private String bearerToken() {
        CurrentUserDTO user = new CurrentUserDTO(10001L, 1L, "alice", "Alice", List.of("USER"));
        return "Bearer " + jwtTokenService.generate(user);
    }
}
