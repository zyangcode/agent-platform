package com.ls.agent.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.core.rag.api.RagEngine;
import com.ls.agent.core.rag.command.IngestKnowledgeDocumentCommand;
import com.ls.agent.core.rag.dto.RagIngestResultDTO;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;
import com.ls.agent.web.dto.CreateRagDocumentRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RagKnowledgeController.class,
        properties = {
                "security.jwt.secret=test-secret-test-secret-test-secret-test",
                "security.jwt.expires-in-seconds=7200"
        }
)
@Import(WebMvcTestSupport.class)
class RagKnowledgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private RagEngine ragEngine;

    @Test
    void ingestDocumentDelegatesWithCurrentUserAndScope() throws Exception {
        when(ragEngine.ingest(any(IngestKnowledgeDocumentCommand.class)))
                .thenReturn(new RagIngestResultDTO(90001L, "Basketball Safety", "hash-1", 2, "INDEXED"));

        mockMvc.perform(post("/api/rag/documents")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRagDocumentRequest(
                                20001L,
                                50001L,
                                "Basketball Safety",
                                "MANUAL",
                                "kb://sports/basketball",
                                "Avoid noon exercise during high heat.",
                                300,
                                40
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(90001))
                .andExpect(jsonPath("$.data.chunkCount").value(2))
                .andExpect(jsonPath("$.data.status").value("INDEXED"));

        ArgumentCaptor<IngestKnowledgeDocumentCommand> captor = ArgumentCaptor.forClass(IngestKnowledgeDocumentCommand.class);
        verify(ragEngine).ingest(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().applicationId()).isEqualTo(20001L);
        assertThat(captor.getValue().ownerUserId()).isEqualTo(10001L);
        assertThat(captor.getValue().profileId()).isNull();
        assertThat(captor.getValue().title()).isEqualTo("Basketball Safety");
        assertThat(captor.getValue().content()).contains("Avoid noon");
        assertThat(captor.getValue().chunkTokenBudget()).isEqualTo(300);
        assertThat(captor.getValue().overlapTokens()).isEqualTo(40);
    }

    @Test
    void searchDocumentsDelegatesWithCurrentUserAndScope() throws Exception {
        when(ragEngine.search(1L, 20001L, 10001L, 50001L, "basketball heat", 3))
                .thenReturn(List.of(new RagSearchResultDTO(
                        90001L,
                        91001L,
                        "Basketball Safety",
                        "Avoid noon exercise during high heat.",
                        "kb://sports/basketball",
                        0.88
                )));

        mockMvc.perform(get("/api/rag/search")
                        .header("Authorization", bearerToken())
                        .param("applicationId", "20001")
                        .param("profileId", "50001")
                        .param("query", "basketball heat")
                        .param("topK", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].documentId").value(90001))
                .andExpect(jsonPath("$.data[0].chunkId").value(91001))
                .andExpect(jsonPath("$.data[0].title").value("Basketball Safety"));
    }

    @Test
    void deleteDocumentDelegatesWithCurrentUserAndScope() throws Exception {
        when(ragEngine.delete(1L, 20001L, 10001L, 50001L, 90001L)).thenReturn(1);

        mockMvc.perform(delete("/api/rag/documents/90001")
                        .header("Authorization", bearerToken())
                        .param("applicationId", "20001")
                        .param("profileId", "50001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
    }

    private String bearerToken() {
        CurrentUserDTO user = new CurrentUserDTO(10001L, 1L, "alice", "Alice", List.of("USER"));
        return "Bearer " + jwtTokenService.generate(user);
    }
}
