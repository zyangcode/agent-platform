package com.ls.agent.core.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ls.agent.core.agent.api.ConversationRepository;
import com.ls.agent.core.agent.entity.ConversationEntity;
import com.ls.agent.core.agent.entity.ConversationMessageEntity;
import com.ls.agent.core.agent.mapper.ConversationMapper;
import com.ls.agent.core.agent.mapper.ConversationMessageMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultConversationRepository implements ConversationRepository {

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;

    public DefaultConversationRepository(
            ConversationMapper conversationMapper,
            ConversationMessageMapper messageMapper
    ) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public ConversationEntity findConversationById(Long conversationId) {
        return conversationMapper.selectById(conversationId);
    }

    @Override
    public void insertConversation(ConversationEntity conversation) {
        conversationMapper.insert(conversation);
    }

    @Override
    public void insertMessage(ConversationMessageEntity message) {
        messageMapper.insert(message);
    }

    @Override
    public List<ConversationMessageEntity> listRecentMessages(Long conversationId, int limit) {
        return messageMapper.selectList(
                new LambdaQueryWrapper<ConversationMessageEntity>()
                        .eq(ConversationMessageEntity::getConversationId, conversationId)
                        .orderByDesc(ConversationMessageEntity::getCreatedAt)
                        .last("limit " + Math.max(1, limit))
        );
    }
}
