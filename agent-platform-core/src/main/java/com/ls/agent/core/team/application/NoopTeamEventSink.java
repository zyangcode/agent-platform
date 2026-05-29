package com.ls.agent.core.team.application;

import com.ls.agent.core.team.api.TeamEventSink;
import com.ls.agent.core.team.dto.TeamRuntimeEventDTO;
import org.springframework.stereotype.Component;

@Component
public class NoopTeamEventSink implements TeamEventSink {

    @Override
    public void emit(TeamRuntimeEventDTO event) {
        // Default sink for non-streaming paths; Gateway will provide an SSE adapter later.
    }
}
