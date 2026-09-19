package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryAttemptDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Write boundary for durable deliveries and append-only attempt history. */
public interface EmailDeliveryStore {

    /**
     * Claims one pending or failed attempt in PostgreSQL for a bounded lease.
     * Implementations must return empty when another instance owns a live
     * claim, the expected version is stale, or the delivery is not claimable.
     */
    Optional<EmailDeliveryClaim> claimForAttempt(
            UUID deliveryId,
            long expectedVersion,
            Instant claimedAt,
            Instant expiresAt);

    /** Persists one canonical pending delivery. */
    EmailDeliveryDetails createPending(EmailDeliveryDetails delivery);

    /** Appends one immutable attempt; implementations must reject rewrites. */
    EmailDeliveryAttemptDetails appendAttempt(EmailDeliveryAttemptDetails attempt);

    /**
     * Appends an attempt and finalizes its delivery in one persistence
     * transaction. Implementations must roll back both writes when either
     * operation fails so the attempt history cannot diverge from the
     * delivery counter or optimistic-lock version.
     */
    EmailDeliveryDetails appendAttemptAndFinalize(
            EmailDeliveryAttemptDetails attempt,
            EmailDeliveryStatus status,
            EmailDeliveryFailureCode failureCode,
            String failureMessage,
            Instant attemptedAt,
            Instant sentAt,
            long expectedVersion);

    /**
     * Atomically appends an attempt, finalizes the delivery, and releases the
     * matching database claim. A stale or replaced claim must roll back the
     * entire operation.
     */
    EmailDeliveryDetails appendAttemptAndFinalize(
            EmailDeliveryAttemptDetails attempt,
            EmailDeliveryStatus status,
            EmailDeliveryFailureCode failureCode,
            String failureMessage,
            Instant attemptedAt,
            Instant sentAt,
            long expectedVersion,
            UUID claimToken);

    /**
     * Finalizes a delivery under optimistic locking. A failed retry may keep
     * the status as {@code FAILED} while updating safe failure metadata.
     */
    EmailDeliveryDetails finalizeAttempt(
            UUID deliveryId,
            EmailDeliveryStatus status,
            EmailDeliveryFailureCode failureCode,
            String failureMessage,
            Instant attemptedAt,
            Instant sentAt,
            long expectedVersion);

    /** Finalizes an attempt only while the matching database claim is owned. */
    EmailDeliveryDetails finalizeAttempt(
            UUID deliveryId,
            EmailDeliveryStatus status,
            EmailDeliveryFailureCode failureCode,
            String failureMessage,
            Instant attemptedAt,
            Instant sentAt,
            long expectedVersion,
            UUID claimToken);
}
