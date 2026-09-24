package io.github.guillermodubon.coachgym.access.domain;

import io.github.guillermodubon.coachgym.access.AccessResult;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Deterministic policy for a bounded duplicate-scan interval.
 *
 * <p>The interval is deliberately supplied by the caller. The current
 * blueprint describes immediate duplicate protection but does not define a
 * numeric duration or persisted setting, so this policy introduces no
 * default. The application obtains its window from deployment configuration
 * and fails closed when that configuration is absent.</p>
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

    /**
     * Evaluates the cross-branch anti-passback rule for a successful access
     * by the same client. The existing same-branch credential rule remains
     * independent and continues to use {@link #evaluate(Instant, Instant)}.
     *
     * <p>Callers must query authoritative persisted history and supply only
     * server timestamps. A denied prior attempt or a different client never
     * consumes the cross-branch duplicate window.</p>
     */
    public DuplicateScanResult evaluateAcrossBranches(
            UUID currentClientId,
            UUID currentBranchId,
            Instant currentAttemptAt,
            UUID previousClientId,
            UUID previousBranchId,
            AccessResult previousResult,
            Instant previousAttemptAt) {

        if (currentClientId == null || currentBranchId == null) {
            throw new IllegalArgumentException(
                    "Current client and branch IDs must be provided.");
        }
        if (currentAttemptAt == null) {
            throw new IllegalArgumentException(
                    "Current access timestamp must be provided.");
        }
        if (previousAttemptAt == null) {
            return DuplicateScanResult.NOT_DUPLICATE;
        }
        if (previousClientId == null || previousBranchId == null
                || previousResult == null) {
            throw new IllegalArgumentException(
                    "Prior access identity, branch, and result must be provided.");
        }

        if (previousResult != AccessResult.ALLOWED
                || !currentClientId.equals(previousClientId)
                || currentBranchId.equals(previousBranchId)) {
            return DuplicateScanResult.NOT_DUPLICATE;
        }
        return evaluate(currentAttemptAt, previousAttemptAt);
    }
}
