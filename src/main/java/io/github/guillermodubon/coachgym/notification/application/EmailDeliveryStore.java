package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryAttemptDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryStatus;
import java.time.Instant;
import java.util.UUID;

/** Write boundary for durable deliveries and append-only attempt history. */
public interface EmailDeliveryStore {

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
}
