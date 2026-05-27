package com.ls.agent.core.agent.application;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.api.ConversationRepository;
import com.ls.agent.core.agent.api.MessageHistoryService;
import com.ls.agent.core.agent.dto.ConversationMessageDTO;
import com.ls.agent.core.agent.dto.ConversationSummaryDTO;
import com.ls.agent.core.agent.entity.ConversationEntity;
import com.ls.agent.core.agent.entity.ConversationMessageEntity;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class DefaultMessageHistoryService implements MessageHistoryService {

    private final ConversationRepository conversationRepository;

    public DefaultMessageHistoryService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @Override
    public List<ConversationSummaryDTO> listConversations(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            int limit
    ) {
        return conversationRepository.listConversations(
                        tenantId,
                        applicationId,
                        userId,
                        profileId,
                        Math.max(1, Math.min(limit, 50))
                ).stream()
                .map(conversation -> new ConversationSummaryDTO(
                        conversation.getId(),
                        conversation.getApplicationId(),
                        conversation.getProfileId(),
                        conversation.getTitle(),
                        conversation.getChannel(),
                        conversation.getStatus(),
                        conversation.getCreatedAt(),
                        conversation.getUpdatedAt()
                ))
                .toList();
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
        requireScopedConversation(tenantId, applicationId, userId, profileId, conversationId);

        var messages = conversationRepository.listRecentMessages(
                conversationId,
                Math.max(1, limit)
        );
        return messages.stream()
                .sorted(this::compareMessageOrder)
                .map(message -> new ConversationMessageDTO(
                        message.getId(),
                        message.getRole(),
                        message.getContent(),
                        message.getTokenCount(),
                        message.getTraceId()))
                .toList();
    }

    @Override
    public void archiveConversation(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            Long conversationId
    ) {
        if (conversationId == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Conversation is unavailable");
        }
        requireScopedConversation(tenantId, applicationId, userId, profileId, conversationId);
        conversationRepository.archiveConversation(conversationId);
    }

    private ConversationEntity requireScopedConversation(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            Long conversationId
    ) {
        ConversationEntity conversation = conversationRepository.findConversationById(conversationId);
        if (conversation == null
                || !tenantId.equals(conversation.getTenantId())
                || !applicationId.equals(conversation.getApplicationId())
                || !userId.equals(conversation.getUserId())
                || !profileId.equals(conversation.getProfileId())
                || "ARCHIVED".equalsIgnoreCase(conversation.getStatus())) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Conversation is unavailable");
        }
        return conversation;
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
