package com.ls.agent.core.agent.api;

import com.ls.agent.core.agent.dto.ConversationMessageDTO;
import com.ls.agent.core.agent.dto.ConversationSummaryDTO;

import java.util.List;

public interface MessageHistoryService {

    List<ConversationSummaryDTO> listConversations(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            int limit
    );

    List<ConversationMessageDTO> listRecentMessages(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            Long conversationId,
            int limit
    );

    void archiveConversation(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            Long conversationId
    );
}
