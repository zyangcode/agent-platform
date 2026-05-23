package com.ls.agent.core.security;

import com.ls.agent.core.security.application.DefaultSecurityEventService;
import com.ls.agent.core.security.command.RecordSecurityEventCommand;
import com.ls.agent.core.security.entity.SecurityEventEntity;
import com.ls.agent.core.security.mapper.SecurityEventMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultSecurityEventServiceTest {

    private final SecurityEventMapper eventMapper = mock(SecurityEventMapper.class);
    private final DefaultSecurityEventService service = new DefaultSecurityEventService(eventMapper);

    @Test
    void recordPersistsSecurityEventWithoutRawText() {
        service.record(new RecordSecurityEventCommand(
                "tr_1",
                1L,
                20001L,
                10001L,
                "PHONE",
                "REQUEST_MESSAGE",
                "hash",
                "138****5678",
                "BLOCK"
        ));

        ArgumentCaptor<SecurityEventEntity> captor = ArgumentCaptor.forClass(SecurityEventEntity.class);
        verify(eventMapper).insert(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("PHONE");
        assertThat(captor.getValue().getSourceTextHash()).isEqualTo("hash");
        assertThat(captor.getValue().getMaskedSample()).isEqualTo("138****5678");
    }

    @Test
    void recordFailureDoesNotEscapeSecurityEventService() {
        doThrow(new IllegalStateException("db down")).when(eventMapper).insert(any(SecurityEventEntity.class));

        assertThatCode(() -> service.record(new RecordSecurityEventCommand(
                "tr_1",
                1L,
                20001L,
                10001L,
                "PHONE",
                "REQUEST_MESSAGE",
                "hash",
                "138****5678",
                "BLOCK"
        ))).doesNotThrowAnyException();
    }
}
