package io.github.guillermodubon.coachgym.user.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Bounded retry policy for best-effort activation notices. */
@Component
public final class StaffAccountActivationRetryPolicy {

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration CLAIM_LEASE = Duration.ofMinutes(2);
    private static final Duration INITIAL_DELAY = Duration.ofMinutes(1);
    private static final Duration MAX_DELAY = Duration.ofHours(1);

    public Instant retryAt(int failedAttemptNumber, Instant completedAt) {
        if (failedAttemptNumber < 1 || failedAttemptNumber >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Failed activation attempt is outside retry bounds.");
        }
        Objects.requireNonNull(completedAt, "Activation attempt completion time is required.");
        long multiplier = 1L << Math.min(failedAttemptNumber - 1, 20);
        Duration delay = INITIAL_DELAY.multipliedBy(multiplier);
        if (delay.compareTo(MAX_DELAY) > 0) {
            delay = MAX_DELAY;
        }
        return completedAt.plus(delay);
    }
}
