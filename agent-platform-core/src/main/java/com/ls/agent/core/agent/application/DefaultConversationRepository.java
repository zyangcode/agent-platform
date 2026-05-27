package com.ls.agent.core.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ls.agent.core.agent.api.ConversationRepository;
import com.ls.agent.core.agent.entity.ConversationEntity;
import com.ls.agent.core.agent.entity.ConversationMessageEntity;
import com.ls.agent.core.agent.mapper.ConversationMapper;
import com.ls.agent.core.agent.mapper.ConversationMessageMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    public void touchConversation(Long conversationId) {
        if (conversationId == null) {
            return;
        }
        ConversationEntity entity = new ConversationEntity();
        entity.setId(conversationId);
        entity.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(entity);
    }

    @Override
    public void archiveConversation(Long conversationId) {
        if (conversationId == null) {
            return;
        }
        ConversationEntity entity = new ConversationEntity();
        entity.setId(conversationId);
        entity.setStatus("ARCHIVED");
        entity.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(entity);
    }

    @Override
    public List<ConversationEntity> listConversations(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            int limit
    ) {
        LambdaQueryWrapper<ConversationEntity> wrapper = new LambdaQueryWrapper<ConversationEntity>()
                .eq(ConversationEntity::getTenantId, tenantId)
                .eq(ConversationEntity::getUserId, userId)
                .eq(applicationId != null, ConversationEntity::getApplicationId, applicationId)
                .eq(profileId != null, ConversationEntity::getProfileId, profileId)
                .ne(ConversationEntity::getStatus, "ARCHIVED")
                .orderByDesc(ConversationEntity::getUpdatedAt)
                .orderByDesc(ConversationEntity::getId)
                .last("limit " + Math.max(1, limit));
        return conversationMapper.selectList(wrapper);
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
