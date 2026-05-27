package com.ls.agent.core.agent;

import com.ls.agent.core.agent.application.DefaultConversationRepository;
import com.ls.agent.core.agent.entity.ConversationEntity;
import com.ls.agent.core.agent.entity.ConversationMessageEntity;
import com.ls.agent.core.agent.mapper.ConversationMapper;
import com.ls.agent.core.agent.mapper.ConversationMessageMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultConversationRepositoryTest {

    private final ConversationMapper conversationMapper = mock(ConversationMapper.class);
    private final ConversationMessageMapper messageMapper = mock(ConversationMessageMapper.class);
    private final DefaultConversationRepository repository = new DefaultConversationRepository(
            conversationMapper,
            messageMapper
    );

    @Test
    void repositoryDelegatesConversationAndMessagePersistenceToMappers() {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(90001L);
        when(conversationMapper.selectById(90001L)).thenReturn(conversation);

        assertThat(repository.findConversationById(90001L)).isSameAs(conversation);

        ConversationEntity newConversation = new ConversationEntity();
        repository.insertConversation(newConversation);
        repository.insertMessage(new ConversationMessageEntity());

        verify(conversationMapper).insert(newConversation);
        verify(messageMapper).insert(any(ConversationMessageEntity.class));
    }

    @Test
    void repositoryListsAndTouchesConversations() {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(90001L);
        when(conversationMapper.selectList(any())).thenReturn(List.of(conversation));

        assertThat(repository.listConversations(1L, 20001L, 10001L, 50001L, 20)).containsExactly(conversation);

        repository.touchConversation(90001L);

        verify(conversationMapper).selectList(any());
        verify(conversationMapper).updateById(any(ConversationEntity.class));
    }

    @Test
    void repositoryArchivesConversationByUpdatingStatus() {
        repository.archiveConversation(90001L);

        verify(conversationMapper).updateById(any(ConversationEntity.class));
    }
}
