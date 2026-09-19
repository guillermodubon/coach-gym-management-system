package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptRateLimiterTest {

    @Test
    void limitsFailedAttemptsPerNormalizedIdentifierAndResetsOnSuccess() {
        Instant start = Instant.parse("2026-09-18T12:00:00Z");
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                new LoginRateLimitProperties(2, Duration.ofMinutes(1)),
                Clock.fixed(start, ZoneOffset.UTC));

        assertThat(limiter.isAllowed(" Coach-Admin ")).isTrue();
        limiter.recordFailure("coach-admin");
        limiter.recordFailure("COACH-ADMIN");
        assertThat(limiter.isAllowed("coach-admin")).isFalse();
        limiter.clear("coach-admin");
        assertThat(limiter.isAllowed("coach-admin")).isTrue();
    }

    @Test
    void expiresTheFailureWindowWithoutRetainingOldIdentifiers() {
        Instant start = Instant.parse("2026-09-18T12:00:00Z");
        MutableClock clock = new MutableClock(start);
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                new LoginRateLimitProperties(1, Duration.ofSeconds(30)),
                clock);

        limiter.recordFailure("coach-admin");
        clock.current = start.plusSeconds(31);

        assertThat(limiter.isAllowed("coach-admin")).isTrue();
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
