package com.ls.agent.core.team.graph;

import com.ls.agent.core.team.api.TeamEventSink;
import com.ls.agent.core.team.application.NoopTeamEventSink;
import com.ls.agent.core.team.application.TeamRunLimiter;

public final class TeamGraphRuntimeContext {

    public static final String METADATA_KEY = TeamGraphRuntimeContext.class.getName();

    private final TeamEventSink eventSink;
    private final TeamRunLimiter limiter;
    private final Long runSpanId;

    public TeamGraphRuntimeContext(TeamEventSink eventSink, TeamRunLimiter limiter, Long runSpanId) {
        this.eventSink = eventSink;
        this.limiter = limiter;
        this.runSpanId = runSpanId;
    }

    public static TeamGraphRuntimeContext noop() {
        return new TeamGraphRuntimeContext(new NoopTeamEventSink(), null, null);
    }

    public TeamEventSink eventSink() {
        return eventSink;
    }

    public TeamRunLimiter limiter() {
        return limiter;
    }

    public Long runSpanId() {
        return runSpanId;
    }
}
