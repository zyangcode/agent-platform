package com.ls.agent.web.controller;

import com.ls.agent.common.response.ApiResponse;
import com.ls.agent.core.rag.api.RagEngine;
import com.ls.agent.core.rag.command.IngestKnowledgeDocumentCommand;
import com.ls.agent.core.rag.dto.RagIngestResultDTO;
import com.ls.agent.core.rag.dto.RagSearchResultDTO;
import com.ls.agent.web.dto.CreateRagDocumentRequest;
import com.ls.agent.web.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RagKnowledgeController {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 20;

    private final RagEngine ragEngine;

    public RagKnowledgeController(RagEngine ragEngine) {
        this.ragEngine = ragEngine;
    }

    @PostMapping("/api/rag/documents")
    public ApiResponse<RagIngestResultDTO> ingest(
            CurrentUser currentUser,
            @Valid @RequestBody CreateRagDocumentRequest request
    ) {
        return ApiResponse.success(ragEngine.ingest(new IngestKnowledgeDocumentCommand(
                currentUser.tenantId(),
                request.applicationId(),
                currentUser.userId(),
                null,
                request.title(),
                request.sourceType(),
                request.sourceUri(),
                request.content(),
                request.chunkTokenBudget() == null ? 0 : request.chunkTokenBudget(),
                request.overlapTokens() == null ? 0 : request.overlapTokens()
        )));
    }

    @GetMapping("/api/rag/search")
    public ApiResponse<List<RagSearchResultDTO>> search(
            CurrentUser currentUser,
            @RequestParam("applicationId") Long applicationId,
            @RequestParam(name = "profileId", required = false) Long profileId,
            @RequestParam("query") String query,
            @RequestParam(name = "topK", defaultValue = "" + DEFAULT_TOP_K) int topK
    ) {
        return ApiResponse.success(ragEngine.search(
                currentUser.tenantId(),
                applicationId,
                currentUser.userId(),
                profileId,
                query,
                clampTopK(topK)
        ));
    }

    @DeleteMapping("/api/rag/documents/{documentId}")
    public ApiResponse<Integer> delete(
            CurrentUser currentUser,
            @PathVariable("documentId") Long documentId,
            @RequestParam("applicationId") Long applicationId,
            @RequestParam(name = "profileId", required = false) Long profileId
    ) {
        return ApiResponse.success(ragEngine.delete(
                currentUser.tenantId(),
                applicationId,
                currentUser.userId(),
                profileId,
                documentId
        ));
    }

    private int clampTopK(int topK) {
        if (topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }
}
