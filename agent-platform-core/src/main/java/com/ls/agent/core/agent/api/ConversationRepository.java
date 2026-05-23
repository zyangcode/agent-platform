package com.ls.agent.core.agent.api;

import com.ls.agent.core.agent.entity.ConversationEntity;
import com.ls.agent.core.agent.entity.ConversationMessageEntity;

import java.util.List;

public interface ConversationRepository {

    ConversationEntity findConversationById(Long conversationId);

    void insertConversation(ConversationEntity conversation);

    void insertMessage(ConversationMessageEntity message);

    List<ConversationMessageEntity> listRecentMessages(Long conversationId, int limit);
}
