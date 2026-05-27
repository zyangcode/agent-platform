package com.ls.agent.web.controller;

import com.ls.agent.common.response.ApiResponse;
import com.ls.agent.core.agent.api.MessageHistoryService;
import com.ls.agent.core.agent.dto.ConversationMessageDTO;
import com.ls.agent.core.agent.dto.ConversationSummaryDTO;
import com.ls.agent.web.security.CurrentUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ConversationController {

    private final MessageHistoryService messageHistoryService;

    public ConversationController(MessageHistoryService messageHistoryService) {
        this.messageHistoryService = messageHistoryService;
    }

    @GetMapping("/api/conversations")
    public ApiResponse<List<ConversationSummaryDTO>> list(
            CurrentUser currentUser,
            @RequestParam(name = "applicationId", required = false) Long applicationId,
            @RequestParam(name = "profileId", required = false) Long profileId,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        return ApiResponse.success(messageHistoryService.listConversations(
                currentUser.tenantId(),
                applicationId,
                currentUser.userId(),
                profileId,
                Math.min(limit, 50)
        ));
    }

    @GetMapping("/api/conversations/{conversationId}/messages")
    public ApiResponse<List<ConversationMessageDTO>> messages(
            CurrentUser currentUser,
            @PathVariable("conversationId") Long conversationId,
            @RequestParam("applicationId") Long applicationId,
            @RequestParam("profileId") Long profileId,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        return ApiResponse.success(messageHistoryService.listRecentMessages(
                currentUser.tenantId(),
                applicationId,
                currentUser.userId(),
                profileId,
                conversationId,
                Math.min(limit, 100)
        ));
    }

    @DeleteMapping("/api/conversations/{conversationId}")
    public ApiResponse<Void> archive(
            CurrentUser currentUser,
            @PathVariable("conversationId") Long conversationId,
            @RequestParam("applicationId") Long applicationId,
            @RequestParam("profileId") Long profileId
    ) {
        messageHistoryService.archiveConversation(
                currentUser.tenantId(),
                applicationId,
                currentUser.userId(),
                profileId,
                conversationId
        );
        return ApiResponse.success(null);
    }
}
