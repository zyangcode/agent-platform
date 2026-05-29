package com.ls.agent.core.team.application;

import com.ls.agent.common.error.BizException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamRunLimiterTest {

    @Test
    void allowsCallsWithinConfiguredLimits() {
        TeamRunLimiter limiter = new TeamRunLimiter(2, 1, 2, 3, 1_000L, fixedClock());

        assertThatCode(() -> {
            limiter.checkTaskCount(2);
            limiter.consumeModelCall();
            limiter.consumeModelCall();
            limiter.consumeToolCall();
            limiter.consumeRetry();
            limiter.checkTimeout();
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsTaskModelToolRetryAndTimeoutLimits() {
        TeamRunLimiter limiter = new TeamRunLimiter(1, 0, 0, 0, 1L, fixedClock());

        assertThatThrownBy(() -> limiter.checkTaskCount(2)).isInstanceOf(BizException.class);
        assertThatThrownBy(limiter::consumeModelCall).isInstanceOf(BizException.class);
        assertThatThrownBy(limiter::consumeToolCall).isInstanceOf(BizException.class);
        assertThatThrownBy(limiter::consumeRetry).isInstanceOf(BizException.class);

        TeamRunLimiter timeoutLimiter = new TeamRunLimiter(
                1,
                1,
                1,
                1,
                1L,
                Clock.fixed(Instant.parse("2026-05-29T00:00:00Z").plus(Duration.ofMillis(2)), ZoneOffset.UTC),
                Instant.parse("2026-05-29T00:00:00Z")
        );
        assertThatThrownBy(timeoutLimiter::checkTimeout).isInstanceOf(BizException.class);
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-05-29T00:00:00Z"), ZoneOffset.UTC);
    }
}
