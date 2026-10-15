package io.github.guillermodubon.coachgym.notification.web;

import io.github.guillermodubon.coachgym.notification.EmailAttemptResult;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryAttemptDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Privacy-safe attempt projection; provider messages and failure text are omitted. */
@Schema(description = "Append-only email attempt metadata without provider details or body data.")
record EmailDeliveryAttemptResponse(
        UUID id,
        UUID deliveryId,
        int attemptNumber,
        EmailAttemptResult result,
        Instant startedAt,
        Instant completedAt,
        EmailDeliveryFailureCode failureCode,
        UUID attemptedByUserId) {

    static EmailDeliveryAttemptResponse from(EmailDeliveryAttemptDetails details) {
        return new EmailDeliveryAttemptResponse(
                details.id(),
                details.deliveryId(),
                details.attemptNumber(),
                details.result(),
                details.startedAt(),
                details.completedAt(),
                details.failureCode(),
                details.attemptedByUserId());
    }
}
