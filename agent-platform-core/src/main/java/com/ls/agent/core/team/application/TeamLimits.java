package com.ls.agent.core.team.application;

public final class TeamLimits {

    public static final int DEFAULT_MAX_TASKS = 8;
    public static final int DEFAULT_MAX_RETRIES = 1;
    public static final int DEFAULT_MAX_TOOL_CALLS = 8;
    public static final int DEFAULT_MAX_MODEL_CALLS = 16;
    public static final long DEFAULT_TIMEOUT_MS = 120_000L;

    private TeamLimits() {
    }
}
