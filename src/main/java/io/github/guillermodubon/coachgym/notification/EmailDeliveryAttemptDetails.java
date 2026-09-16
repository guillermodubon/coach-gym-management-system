package io.github.guillermodubon.coachgym.notification;

import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValuePolicy;
import java.time.Instant;
import java.util.UUID;

/** Immutable append-only operational snapshot for one send attempt. */
public record EmailDeliveryAttemptDetails(
        UUID id,
        UUID deliveryId,
        int attemptNumber,
        EmailAttemptResult result,
        Instant startedAt,
        Instant completedAt,
        EmailDeliveryFailureCode failureCode,
        String failureMessage,
        UUID attemptedByUserId,
        String providerMessageId) {

    public EmailDeliveryAttemptDetails(
            UUID id,
            UUID deliveryId,
            int attemptNumber,
            EmailAttemptResult result,
            Instant startedAt,
            Instant completedAt,
            EmailDeliveryFailureCode failureCode,
            String failureMessage,
            UUID attemptedByUserId) {
        this(id, deliveryId, attemptNumber, result, startedAt, completedAt,
                failureCode, failureMessage, attemptedByUserId, null);
    }

    public EmailDeliveryAttemptDetails {
        if (id == null || deliveryId == null) {
            throw new IllegalArgumentException("Email attempt and delivery ids are required.");
        }
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("Email attempt number must be positive.");
        }
        if (result == null) {
            throw new IllegalArgumentException("Email attempt result is required.");
        }
        if (startedAt == null || completedAt == null || completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Email attempt timestamps are invalid.");
        }
        if (result == EmailAttemptResult.SENT) {
            if (failureCode != null || failureMessage != null) {
                throw new IllegalArgumentException("A successful email attempt cannot contain failure metadata.");
            }
        } else {
            if (failureCode == null || failureMessage == null) {
                throw new IllegalArgumentException("An unsuccessful email attempt requires safe failure metadata.");
            }
            if (result == EmailAttemptResult.AMBIGUOUS
                    && failureCode != EmailDeliveryFailureCode.AMBIGUOUS_TRANSPORT_OUTCOME) {
                throw new IllegalArgumentException(
                        "An ambiguous email attempt requires the ambiguous transport failure code.");
            }
            failureMessage = EmailDeliveryValuePolicy.normalizeFailureMessage(failureMessage);
        }
        if (attemptedByUserId == null) {
            throw new IllegalArgumentException("Email attempt actor id is required.");
        }
        providerMessageId = EmailDeliveryValuePolicy.normalizeOptionalText(
                providerMessageId, "Email provider message id", 200);
    }

    @Override
    public String toString() {
        return "EmailDeliveryAttemptDetails[id=" + id
                + ", deliveryId=" + deliveryId
                + ", attemptNumber=" + attemptNumber
                + ", result=" + result
                + ", startedAt=" + startedAt
                + ", completedAt=" + completedAt
                + ", failureCode=" + failureCode
                + ", failureMessagePresent=" + (failureMessage != null)
                + ", attemptedByUserId=" + attemptedByUserId
                + ", providerMessageIdPresent=" + (providerMessageId != null)
                + ']';
    }
}
