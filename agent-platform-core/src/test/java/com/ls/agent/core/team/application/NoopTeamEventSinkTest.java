package com.ls.agent.core.team.application;

import com.ls.agent.core.team.dto.TeamRuntimeEventDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class NoopTeamEventSinkTest {

    @Test
    void ignoresNullAndNonNullEvents() {
        NoopTeamEventSink sink = new NoopTeamEventSink();

        assertThatCode(() -> sink.emit(null)).doesNotThrowAnyException();
        assertThatCode(() -> sink.emit(TeamRuntimeEventDTO.start("trace-1", 1, "team started", null)))
                .doesNotThrowAnyException();
    }
}
