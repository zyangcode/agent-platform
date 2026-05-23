package com.ls.agent.core.trace;

import com.ls.agent.core.trace.application.DefaultTokenUsageService;
import com.ls.agent.core.trace.command.RecordTokenUsageCommand;
import com.ls.agent.core.trace.entity.TokenUsageLogEntity;
import com.ls.agent.core.trace.mapper.TokenUsageLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultTokenUsageServiceTest {

    private final TokenUsageLogMapper tokenUsageMapper = mock(TokenUsageLogMapper.class);
    private final DefaultTokenUsageService service = new DefaultTokenUsageService(tokenUsageMapper);

    @Test
    void recordInsertsTokenUsageLog() {
        service.record(new RecordTokenUsageCommand(
                "tr_1",
                100L,
                1L,
                20001L,
                10001L,
                50001L,
                30001L,
                1L,
                "mock-chat",
                "MOCK",
                2,
                3,
                5,
                true
        ));

        ArgumentCaptor<TokenUsageLogEntity> captor = ArgumentCaptor.forClass(TokenUsageLogEntity.class);
        verify(tokenUsageMapper).insert(captor.capture());
        assertThat(captor.getValue().getTraceId()).isEqualTo("tr_1");
        assertThat(captor.getValue().getModelName()).isEqualTo("mock-chat");
        assertThat(captor.getValue().getTotalTokens()).isEqualTo(5);
        assertThat(captor.getValue().getEstimated()).isTrue();
    }

    @Test
    void mapperFailureDoesNotEscapeTokenUsageService() {
        doThrow(new IllegalStateException("db down")).when(tokenUsageMapper).insert(any(TokenUsageLogEntity.class));

        assertThatCode(() -> service.record(new RecordTokenUsageCommand(
                "tr_1",
                null,
                1L,
                null,
                null,
                null,
                30001L,
                1L,
                "mock-chat",
                "MOCK",
                0,
                0,
                0,
                true
        ))).doesNotThrowAnyException();
    }
}
