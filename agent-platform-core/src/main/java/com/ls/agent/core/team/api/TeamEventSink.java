package com.ls.agent.core.team.api;

import com.ls.agent.core.team.dto.TeamRuntimeEventDTO;

public interface TeamEventSink {

    void emit(TeamRuntimeEventDTO event);
}
