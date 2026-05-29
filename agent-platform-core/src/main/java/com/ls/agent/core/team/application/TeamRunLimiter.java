package com.ls.agent.core.team.application;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class TeamRunLimiter {

    private final int maxTasks;
    private final int maxRetries;
    private final int maxToolCalls;
    private final int maxModelCalls;
    private final long timeoutMs;
    private final Clock clock;
    private final Instant startedAt;
    private int retryCount;
    private int toolCallCount;
    private int modelCallCount;

    public TeamRunLimiter() {
        this(
                TeamLimits.DEFAULT_MAX_TASKS,
                TeamLimits.DEFAULT_MAX_RETRIES,
                TeamLimits.DEFAULT_MAX_TOOL_CALLS,
                TeamLimits.DEFAULT_MAX_MODEL_CALLS,
                TeamLimits.DEFAULT_TIMEOUT_MS
        );
    }

    public TeamRunLimiter(int maxTasks, int maxRetries, int maxToolCalls, int maxModelCalls, long timeoutMs) {
        this(maxTasks, maxRetries, maxToolCalls, maxModelCalls, timeoutMs, Clock.systemUTC());
    }

    public TeamRunLimiter(
            int maxTasks,
            int maxRetries,
            int maxToolCalls,
            int maxModelCalls,
            long timeoutMs,
            Clock clock
    ) {
        this(maxTasks, maxRetries, maxToolCalls, maxModelCalls, timeoutMs, clock, Instant.now(clock));
    }

    TeamRunLimiter(
            int maxTasks,
            int maxRetries,
            int maxToolCalls,
            int maxModelCalls,
            long timeoutMs,
            Clock clock,
            Instant startedAt
    ) {
        this.maxTasks = maxTasks;
        this.maxRetries = maxRetries;
        this.maxToolCalls = maxToolCalls;
        this.maxModelCalls = maxModelCalls;
        this.timeoutMs = timeoutMs;
        this.clock = clock;
        this.startedAt = startedAt;
    }

    public TeamRunLimiter newRun() {
        return new TeamRunLimiter(maxTasks, maxRetries, maxToolCalls, maxModelCalls, timeoutMs, clock);
    }

    public void checkTaskCount(int taskCount) {
        if (taskCount > maxTasks) {
            fail("Team task count exceeds maxTasks");
        }
    }

    public void consumeRetry() {
        retryCount++;
        if (retryCount > maxRetries) {
            fail("Team retry count exceeds maxRetries");
        }
    }

    public void consumeToolCall() {
        toolCallCount++;
        if (toolCallCount > maxToolCalls) {
            fail("Team tool call count exceeds maxToolCalls");
        }
    }

    public void consumeModelCall() {
        modelCallCount++;
        if (modelCallCount > maxModelCalls) {
            fail("Team model call count exceeds maxModelCalls");
        }
    }

    public void consumeModelCalls(int count) {
        for (int index = 0; index < count; index++) {
            consumeModelCall();
        }
    }

    public void checkTimeout() {
        if (Duration.between(startedAt, Instant.now(clock)).toMillis() > timeoutMs) {
            fail("Team run timeout exceeded");
        }
    }

    private void fail(String message) {
        throw new BizException(ErrorCode.AGENT_MAX_STEPS_EXCEEDED, message);
    }
}
