package io.github.guillermodubon.coachgym.notification.domain;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryStatus;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryRetryLimitExceededException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryStateConflictException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryVersionConflictException;
import java.time.Duration;
import java.time.Instant;

/** Pure lifecycle, optimistic-lock, and bounded manual-retry policy. */
public final class EmailDeliveryLifecyclePolicy {

    /** Default maximum number of retries after the initial send attempt. */
    public static final int DEFAULT_MAX_RETRY_COUNT = 3;

    private final int maximumRetryCount;
    private final Duration stalePendingThreshold;

    public EmailDeliveryLifecyclePolicy() {
        this(DEFAULT_MAX_RETRY_COUNT, Duration.ofMinutes(15));
    }

    public EmailDeliveryLifecyclePolicy(int maximumRetryCount) {
        this(maximumRetryCount, Duration.ofMinutes(15));
    }

    public EmailDeliveryLifecyclePolicy(
            int maximumRetryCount, Duration stalePendingThreshold) {
        if (maximumRetryCount < 0) {
            throw new IllegalArgumentException("Maximum email retry count must not be negative.");
        }
        if (stalePendingThreshold == null
                || stalePendingThreshold.isZero()
                || stalePendingThreshold.isNegative()
                || stalePendingThreshold.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException(
                    "Stale pending email threshold must be positive and at most 24 hours.");
        }
        this.maximumRetryCount = maximumRetryCount;
        this.stalePendingThreshold = stalePendingThreshold;
    }

    public int maximumRetryCount() {
        return maximumRetryCount;
    }

    public Duration stalePendingThreshold() {
        return stalePendingThreshold;
    }

    /**
     * Identifies a pending intent that needs an explicit operational decision.
     * This method never authorizes an automatic resend.
     */
    public boolean isStalePending(EmailDeliveryDetails delivery, Instant now) {
        if (delivery == null || now == null || delivery.status() != EmailDeliveryStatus.PENDING) {
            return false;
        }
        return !now.isBefore(delivery.requestedAt().plus(stalePendingThreshold));
    }

    public boolean canTransition(
            EmailDeliveryStatus current, EmailDeliveryStatus requested) {
        return current != null && current.canTransitionTo(requested);
    }

    public void requireTransition(
            EmailDeliveryDetails delivery,
            EmailDeliveryStatus requestedStatus) {
        if (delivery == null || requestedStatus == null) {
            throw new EmailDeliveryValidationException(
                    "Email delivery and requested status are required.");
        }
        if (!canTransition(delivery.status(), requestedStatus)) {
            throw new EmailDeliveryStateConflictException(
                    delivery.id(), delivery.status(), requestedStatus);
        }
    }

    public boolean isRetryEligible(EmailDeliveryStatus status) {
        return status == EmailDeliveryStatus.FAILED;
    }

    /**
     * An ambiguous transport result is not a safe retry candidate: the
     * provider may already have accepted the message. It remains FAILED for
     * reporting, but requires an explicit operational reconciliation instead
     * of another blind send.
     */
    public boolean isRetryEligible(EmailDeliveryDetails delivery) {
        return delivery != null
                && isRetryEligible(delivery.status())
                && delivery.lastFailureCode()
                != io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode
                        .AMBIGUOUS_TRANSPORT_OUTCOME;
    }

    /**
     * Validates an explicit retry against the persisted state and version.
     * {@code attemptCount} includes the initial attempt; the configured limit
     * counts only retries after that initial attempt.
     */
    public void requireRetryAllowed(
            EmailDeliveryDetails delivery,
            long expectedVersion,
            int maximumRetryCount) {
        if (delivery == null) {
            throw new EmailDeliveryValidationException("Email delivery is required.");
        }
        if (expectedVersion < 0 || maximumRetryCount < 0) {
            throw new EmailDeliveryValidationException(
                    "Email delivery version and retry limit must not be negative.");
        }
        if (delivery.version() != expectedVersion) {
            throw new EmailDeliveryVersionConflictException(
                    delivery.id(), expectedVersion, delivery.version());
        }
        if (!isRetryEligible(delivery)) {
            throw new EmailDeliveryStateConflictException(
                    delivery.id(), delivery.status(), EmailDeliveryStatus.SENT);
        }
        if (delivery.attemptCount() < 1
                || delivery.attemptCount() - 1 >= maximumRetryCount) {
            throw new EmailDeliveryRetryLimitExceededException(
                    delivery.id(), delivery.attemptCount(), maximumRetryCount);
        }
    }

    /** Validates an explicit retry using the configured retry bound. */
    public void requireRetryAllowed(
            EmailDeliveryDetails delivery,
            long expectedVersion) {
        requireRetryAllowed(delivery, expectedVersion, maximumRetryCount);
    }
}
