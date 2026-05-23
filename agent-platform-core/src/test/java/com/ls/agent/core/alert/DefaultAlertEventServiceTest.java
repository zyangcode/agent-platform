package com.ls.agent.core.alert;

import com.ls.agent.core.alert.application.DefaultAlertEventService;
import com.ls.agent.core.alert.command.RecordAlertEventCommand;
import com.ls.agent.core.alert.entity.AlertEventEntity;
import com.ls.agent.core.alert.mapper.AlertEventMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultAlertEventServiceTest {

    private final AlertEventMapper eventMapper = mock(AlertEventMapper.class);
    private final DefaultAlertEventService service = new DefaultAlertEventService(eventMapper);

    @Test
    void recordPersistsPendingAlertEvent() {
        service.record(new RecordAlertEventCommand(
                "tr_1",
                1L,
                20001L,
                "SECURITY_BLOCKED",
                "WARN",
                "Sensitive request blocked",
                "Request was blocked by security policy",
                "Review security_events by traceId"
        ));

        ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
        verify(eventMapper).insert(captor.capture());
        assertThat(captor.getValue().getTraceId()).isEqualTo("tr_1");
        assertThat(captor.getValue().getTenantId()).isEqualTo(1L);
        assertThat(captor.getValue().getApplicationId()).isEqualTo(20001L);
        assertThat(captor.getValue().getAlertType()).isEqualTo("SECURITY_BLOCKED");
        assertThat(captor.getValue().getLevel()).isEqualTo("WARN");
        assertThat(captor.getValue().getNotifyStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getRetryCount()).isZero();
    }

    @Test
    void recordFailureDoesNotEscapeAlertEventService() {
        doThrow(new IllegalStateException("db down")).when(eventMapper).insert(any(AlertEventEntity.class));

        assertThatCode(() -> service.record(new RecordAlertEventCommand(
                "tr_1",
                1L,
                20001L,
                "MODEL_ERROR",
                "ERROR",
                "Agent runtime failed",
                "Agent failed",
                "Check trace detail"
        ))).doesNotThrowAnyException();
    }
}
