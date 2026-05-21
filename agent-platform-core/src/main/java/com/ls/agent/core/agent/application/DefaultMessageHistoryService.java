package com.ls.agent.core.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.api.MessageHistoryService;
import com.ls.agent.core.agent.dto.ConversationMessageDTO;
import com.ls.agent.core.agent.entity.ConversationEntity;
import com.ls.agent.core.agent.entity.ConversationMessageEntity;
import com.ls.agent.core.agent.mapper.ConversationMapper;
import com.ls.agent.core.agent.mapper.ConversationMessageMapper;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class DefaultMessageHistoryService implements MessageHistoryService {

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;

    public DefaultMessageHistoryService(ConversationMapper conversationMapper, ConversationMessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public List<ConversationMessageDTO> listRecentMessages(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            Long conversationId,
            int limit
    ) {
        if (conversationId == null) {
            return List.of();
        }
        ConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null
                || !tenantId.equals(conversation.getTenantId())
                || !applicationId.equals(conversation.getApplicationId())
                || !userId.equals(conversation.getUserId())
                || !profileId.equals(conversation.getProfileId())) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Conversation is unavailable");
        }

        List<ConversationMessageEntity> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ConversationMessageEntity>()
                        .eq(ConversationMessageEntity::getConversationId, conversationId)
                        .orderByDesc(ConversationMessageEntity::getCreatedAt)
                        .last("limit " + Math.max(1, limit))
        );
        return messages.stream()
                .sorted(this::compareMessageOrder)
                .map(message -> new ConversationMessageDTO(
                        message.getRole(),
                        message.getContent(),
                        message.getTokenCount()))
                .toList();
    }

    private int compareMessageOrder(ConversationMessageEntity left, ConversationMessageEntity right) {
        if (left.getCreatedAt() != null && right.getCreatedAt() != null) {
            int createdAtResult = left.getCreatedAt().compareTo(right.getCreatedAt());
            if (createdAtResult != 0) {
                return createdAtResult;
            }
        }
        if (left.getCreatedAt() == null && right.getCreatedAt() != null) {
            return compareNullableId(left, right);
        }
        if (left.getCreatedAt() != null && right.getCreatedAt() == null) {
            return compareNullableId(left, right);
        }
        return compareNullableId(left, right);
    }

    private int compareNullableId(ConversationMessageEntity left, ConversationMessageEntity right) {
        return Comparator.nullsLast(Long::compareTo).compare(left.getId(), right.getId());
    }
}
