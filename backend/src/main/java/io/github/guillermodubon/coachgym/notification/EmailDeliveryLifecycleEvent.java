package io.github.guillermodubon.coachgym.notification;

import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryLifecyclePolicy;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValuePolicy;
import java.time.Instant;
import java.util.UUID;

/**
 * Privacy-safe lifecycle event emitted after a delivery attempt is finalized.
 *
 * <p>The event intentionally contains no recipient, message body, attachment
 * bytes, provider response, or transport credentials. Later audit consumers can
 * use it without receiving email content or other sensitive values.</p>
 */
public record EmailDeliveryLifecycleEvent(
        UUID deliveryId,
        EmailDeliveryType deliveryType,
        EmailDeliveryStatus previousStatus,
        EmailDeliveryStatus currentStatus,
        EmailAttemptResult attemptResult,
        int attemptNumber,
        EmailDeliveryFailureCode failureCode,
        UUID actorUserId,
        Instant occurredAt,
        UUID sourceResourceId,
        UUID clientId,
        String maskedRecipient,
        String actorIdentifier,
        UUID branchId) {

    /**
     * Compatibility constructor for publishers created before the audit
     * snapshot fields were added. New publishers should provide the complete
     * server-owned snapshot through the canonical constructor.
     */
    public EmailDeliveryLifecycleEvent(
            UUID deliveryId,
            EmailDeliveryType deliveryType,
            EmailDeliveryStatus previousStatus,
            EmailDeliveryStatus currentStatus,
            EmailAttemptResult attemptResult,
            int attemptNumber,
            EmailDeliveryFailureCode failureCode,
            UUID actorUserId,
            Instant occurredAt) {

        this(deliveryId, deliveryType, previousStatus, currentStatus,
                attemptResult, attemptNumber, failureCode, actorUserId,
                occurredAt, null, null, null, null);
    }

    /** Compatibility constructor for the pre-branch audit snapshot shape. */
    public EmailDeliveryLifecycleEvent(
            UUID deliveryId,
            EmailDeliveryType deliveryType,
            EmailDeliveryStatus previousStatus,
            EmailDeliveryStatus currentStatus,
            EmailAttemptResult attemptResult,
            int attemptNumber,
            EmailDeliveryFailureCode failureCode,
            UUID actorUserId,
            Instant occurredAt,
            UUID sourceResourceId,
            UUID clientId,
            String maskedRecipient,
            String actorIdentifier) {
        this(deliveryId, deliveryType, previousStatus, currentStatus,
                attemptResult, attemptNumber, failureCode, actorUserId,
                occurredAt, sourceResourceId, clientId, maskedRecipient,
                actorIdentifier, null);
    }

    /**
     * Creates a lifecycle event from the compact public audit projection.
     * The previous state and transport result are derived from the finalized
     * status and attempt number so consumers do not have to duplicate that
     * policy.
     */
    public EmailDeliveryLifecycleEvent(
            UUID deliveryId,
            EmailDeliveryType deliveryType,
            UUID sourceResourceId,
            UUID clientId,
            String maskedRecipient,
            EmailDeliveryStatus status,
            int attemptNumber,
            EmailDeliveryFailureCode failureCode,
            UUID actorUserId,
            String actorIdentifier,
            Instant occurredAt) {

        this(
                deliveryId,
                deliveryType,
                attemptNumber > 1
                        ? EmailDeliveryStatus.FAILED
                        : EmailDeliveryStatus.PENDING,
                status,
                status == EmailDeliveryStatus.SENT
                        ? EmailAttemptResult.SENT
                        : failureCode == EmailDeliveryFailureCode.AMBIGUOUS_TRANSPORT_OUTCOME
                                ? EmailAttemptResult.AMBIGUOUS
                                : EmailAttemptResult.FAILED,
                attemptNumber,
                failureCode,
                actorUserId,
                occurredAt,
                sourceResourceId,
                clientId,
                maskedRecipient,
                actorIdentifier,
                null);
    }

    public EmailDeliveryLifecycleEvent {
        if (deliveryId == null) {
            throw new IllegalArgumentException("Email delivery id is required.");
        }
        if (deliveryType == null || previousStatus == null || currentStatus == null) {
            throw new IllegalArgumentException("Email delivery lifecycle statuses are required.");
        }
        EmailDeliveryLifecyclePolicy policy = new EmailDeliveryLifecyclePolicy();
        if (!policy.canTransition(previousStatus, currentStatus)) {
            throw new IllegalArgumentException("Email delivery lifecycle transition is invalid.");
        }
        if (currentStatus == EmailDeliveryStatus.PENDING || attemptResult == null) {
            throw new IllegalArgumentException("Finalized email delivery outcome is required.");
        }
        if (attemptNumber < 1 || actorUserId == null || occurredAt == null) {
            throw new IllegalArgumentException("Email lifecycle event metadata is invalid.");
        }
        if (sourceResourceId == null ^ clientId == null) {
            throw new IllegalArgumentException(
                    "Email lifecycle source and client references must be provided together.");
        }
        if (maskedRecipient != null) {
            maskedRecipient = normalizeMaskedRecipient(maskedRecipient);
        }
        if (actorIdentifier != null) {
            actorIdentifier = normalizeActorIdentifier(actorIdentifier);
        }
        if (currentStatus == EmailDeliveryStatus.SENT) {
            if (attemptResult != EmailAttemptResult.SENT || failureCode != null) {
                throw new IllegalArgumentException("Successful email lifecycle metadata is invalid.");
            }
        } else if (attemptResult == EmailAttemptResult.SENT || failureCode == null) {
            throw new IllegalArgumentException("Failed email lifecycle metadata is invalid.");
        }
        if (attemptResult == EmailAttemptResult.AMBIGUOUS
                && failureCode != EmailDeliveryFailureCode.AMBIGUOUS_TRANSPORT_OUTCOME) {
            throw new IllegalArgumentException("Ambiguous email lifecycle metadata is invalid.");
        }
    }

    /** Returns whether this event represents an explicit retry attempt. */
    public boolean retry() {
        return attemptNumber > 1 || previousStatus == EmailDeliveryStatus.FAILED;
    }

    /** Returns the bounded audit action represented by this finalized attempt. */
    public String auditActionCode() {
        if (retry()) {
            return "EMAIL_DELIVERY_RETRIED";
        }
        return currentStatus == EmailDeliveryStatus.SENT
                ? "EMAIL_DELIVERY_SENT"
                : "EMAIL_DELIVERY_FAILED";
    }

    private static String normalizeMaskedRecipient(String value) {
        String normalized = value.strip();
        if (normalized.length() > EmailDeliveryValuePolicy.MAX_RECIPIENT_LENGTH
                || normalized.contains("\r") || normalized.contains("\n")
                || !normalized.contains("***@")) {
            throw new IllegalArgumentException("Masked email recipient is invalid.");
        }
        return normalized;
    }

    private static String normalizeActorIdentifier(String value) {
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > 100
                || normalized.contains("\r") || normalized.contains("\n")) {
            throw new IllegalArgumentException("Email lifecycle actor identifier is invalid.");
        }
        return normalized;
    }
}
