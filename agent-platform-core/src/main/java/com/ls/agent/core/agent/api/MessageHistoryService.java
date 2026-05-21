package com.ls.agent.core.agent.api;

import com.ls.agent.core.agent.dto.ConversationMessageDTO;

import java.util.List;

public interface MessageHistoryService {

    List<ConversationMessageDTO> listRecentMessages(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            Long conversationId,
            int limit
    );
}
