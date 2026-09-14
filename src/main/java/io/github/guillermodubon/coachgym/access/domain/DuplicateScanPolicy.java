package io.github.guillermodubon.coachgym.access.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Deterministic policy for a bounded duplicate-scan interval.
 *
 * <p>The interval is deliberately supplied by the caller. The current
 * blueprint describes immediate duplicate protection but does not define a
 * numeric duration or a persisted setting, so this block introduces no
 * default or configuration value. A later persistence/application block must
 * obtain the approved window and use the server clock when evaluating it.</p>
 *
 * <p>The interval is half-open: an equivalent attempt is a duplicate when its
 * elapsed age is greater than or equal to zero and strictly less than the
 * configured window. An attempt exactly at the window boundary is therefore
 * eligible for normal evaluation.</p>
 */
public record DuplicateScanPolicy(Duration window) {

    public DuplicateScanPolicy {
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException(
                    "Duplicate scan window must be positive.");
        }
    }

    /**
     * Evaluates one current server timestamp against the most recent
     * equivalent attempt.
     *
     * @param currentAttemptAt current timestamp captured from the server clock
     * @param previousAttemptAt timestamp of the most recent equivalent attempt,
     *                          or {@code null} when none exists
     * @return the explicit duplicate outcome
     */
    public DuplicateScanResult evaluate(
            Instant currentAttemptAt,
            Instant previousAttemptAt) {

        if (currentAttemptAt == null) {
            throw new IllegalArgumentException(
                    "Current access timestamp must be provided.");
        }
        if (previousAttemptAt == null) {
            return DuplicateScanResult.NOT_DUPLICATE;
        }

        Duration elapsed = Duration.between(
                previousAttemptAt,
                currentAttemptAt);

        if (!elapsed.isNegative() && elapsed.compareTo(window) < 0) {
            return DuplicateScanResult.DUPLICATE;
        }
        return DuplicateScanResult.NOT_DUPLICATE;
    }
}
