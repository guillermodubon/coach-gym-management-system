package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LoginAttemptRateLimiterTest {

    @Test
    void limitsFailedAttemptsPerNormalizedIdentifierAndResetsOnSuccess() {
        Instant start = Instant.parse("2026-09-18T12:00:00Z");
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                new LoginRateLimitProperties(
                        2, Duration.ofMinutes(1),
                        LoginRateLimitProperties.DEFAULT_MAX_TRACKED_IDENTIFIERS),
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
                new LoginRateLimitProperties(
                        1, Duration.ofSeconds(30),
                        LoginRateLimitProperties.DEFAULT_MAX_TRACKED_IDENTIFIERS),
                clock);

        limiter.recordFailure("coach-admin");
        clock.current = start.plusSeconds(31);

        assertThat(limiter.isAllowed("coach-admin")).isTrue();
    }

    @Test
    void reclaimsExpiredCapacityForANewIdentifier() {
        Instant start = Instant.parse("2026-09-18T12:00:00Z");
        MutableClock clock = new MutableClock(start);
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                new LoginRateLimitProperties(1, Duration.ofSeconds(30), 1),
                clock);

        limiter.recordFailure("first-user");
        assertThat(limiter.isAllowed("first-user")).isFalse();

        clock.current = start.plusSeconds(30);
        limiter.recordFailure("second-user");

        assertThat(limiter.isAllowed("first-user")).isTrue();
        assertThat(limiter.isAllowed("second-user")).isFalse();
    }

    @Test
    void preservesBlockedIdentifiersWhenCapacityIsSaturated() {
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                new LoginRateLimitProperties(
                        1, Duration.ofMinutes(1), 1),
                Clock.fixed(Instant.parse("2026-09-18T12:00:00Z"), ZoneOffset.UTC));

        limiter.recordFailure("blocked-user");
        limiter.recordFailure("unseen-a");
        limiter.recordFailure("unseen-b");

        assertThat(limiter.isAllowed("blocked-user")).isFalse();
        assertThat(limiter.isAllowed("unseen-a")).isTrue();
        assertThat(limiter.isAllowed("unseen-b")).isTrue();
    }

    @Test
    void stopsTrackingNewIdentifiersAtCapacityWithoutBlockingTheirAuthentication() {
        Instant start = Instant.parse("2026-09-18T12:00:00Z");
        MutableClock clock = new MutableClock(start);
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                new LoginRateLimitProperties(5, Duration.ofMinutes(1), 2),
                clock);

        limiter.recordFailure("first-user");
        clock.current = start.plusSeconds(10);
        limiter.recordFailure("second-user");

        assertThat(limiter.isAllowed("first-user")).isTrue();
        assertThat(limiter.isAllowed("new-user")).isTrue();
        assertThat(limiter.retryAfterSeconds("new-user")).isEqualTo(1);
        limiter.recordFailure("new-user");
        assertThat(limiter.isAllowed("new-user")).isTrue();

        clock.current = start.plusSeconds(61);

        assertThat(limiter.isAllowed("new-user")).isTrue();
        assertThat(limiter.isAllowed("second-user")).isTrue();
    }

    @Test
    void clearingAnIdentifierRemovesItsExistingFailureWindow() {
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                new LoginRateLimitProperties(5, Duration.ofMinutes(1), 1),
                Clock.fixed(Instant.parse("2026-09-18T12:00:00Z"), ZoneOffset.UTC));

        limiter.recordFailure("first-user");
        assertThat(limiter.isAllowed("second-user")).isTrue();

        limiter.clear("first-user");

        assertThat(limiter.isAllowed("second-user")).isTrue();
    }

    @Test
    void concurrentHighCardinalityFailuresNeverExceedTheConfiguredCapacity() throws Exception {
        int workers = 16;
        int identifiersPerWorker = 16;
        int capacity = 8;
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                new LoginRateLimitProperties(1, Duration.ofMinutes(1), capacity),
                Clock.fixed(Instant.parse("2026-09-18T12:00:00Z"), ZoneOffset.UTC));
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<String> identifiers = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(workers)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                int workerNumber = worker;
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test start was not released.");
                    }
                    for (int index = 0; index < identifiersPerWorker; index++) {
                        limiter.recordFailure("user-" + workerNumber + "-" + index);
                    }
                    return null;
                }));
                for (int index = 0; index < identifiersPerWorker; index++) {
                    identifiers.add("user-" + worker + "-" + index);
                }
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        }

        long throttled = identifiers.stream().filter(id -> !limiter.isAllowed(id)).count();
        assertThat(throttled).isEqualTo(capacity);
        assertThat(limiter.isAllowed("unseen-user")).isTrue();
    }

    @Test
    void rateLimitWindowsAreLocalToEachApplicationProcess() {
        Instant start = Instant.parse("2026-09-18T12:00:00Z");
        LoginRateLimitProperties properties =
                new LoginRateLimitProperties(1, Duration.ofMinutes(1), 10);
        LoginAttemptRateLimiter firstProcess = new LoginAttemptRateLimiter(
                properties, Clock.fixed(start, ZoneOffset.UTC));
        LoginAttemptRateLimiter secondProcess = new LoginAttemptRateLimiter(
                properties, Clock.fixed(start, ZoneOffset.UTC));

        firstProcess.recordFailure("coach-admin");

        assertThat(firstProcess.isAllowed("coach-admin")).isFalse();
        assertThat(secondProcess.isAllowed("coach-admin")).isTrue();
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
