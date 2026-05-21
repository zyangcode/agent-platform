package com.ls.agent.core.agent;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.core.agent.application.DefaultMessageHistoryService;
import com.ls.agent.core.agent.dto.ConversationMessageDTO;
import com.ls.agent.core.agent.entity.ConversationEntity;
import com.ls.agent.core.agent.entity.ConversationMessageEntity;
import com.ls.agent.core.agent.mapper.ConversationMapper;
import com.ls.agent.core.agent.mapper.ConversationMessageMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultMessageHistoryServiceTest {

    private final ConversationMapper conversationMapper = mock(ConversationMapper.class);
    private final ConversationMessageMapper messageMapper = mock(ConversationMessageMapper.class);
    private final DefaultMessageHistoryService service = new DefaultMessageHistoryService(conversationMapper, messageMapper);

    @Test
    void listRecentMessagesReturnsMessagesInChronologicalOrderWithIdFallback() {
        when(conversationMapper.selectById(90001L)).thenReturn(conversation());
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                message(2L, null, "assistant", "second"),
                message(1L, null, "user", "first"),
                message(3L, LocalDateTime.of(2026, 5, 21, 20, 0), "assistant", "third")
        ));

        List<ConversationMessageDTO> result = service.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 20);

        assertThat(result).extracting("content").containsExactly("first", "second", "third");
    }

    @Test
    void listRecentMessagesRejectsConversationOutsideCurrentScope() {
        ConversationEntity conversation = conversation();
        conversation.setUserId(99999L);
        when(conversationMapper.selectById(90001L)).thenReturn(conversation);

        assertThatThrownBy(() -> service.listRecentMessages(1L, 20001L, 10001L, 50001L, 90001L, 20))
                .isInstanceOf(BizException.class);
    }

    private ConversationEntity conversation() {
        ConversationEntity entity = new ConversationEntity();
        entity.setId(90001L);
        entity.setTenantId(1L);
        entity.setApplicationId(20001L);
        entity.setUserId(10001L);
        entity.setProfileId(50001L);
        return entity;
    }

    private ConversationMessageEntity message(Long id, LocalDateTime createdAt, String role, String content) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setId(id);
        entity.setCreatedAt(createdAt);
        entity.setRole(role);
        entity.setContent(content);
        entity.setTokenCount(1);
        return entity;
    }
}
