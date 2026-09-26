package io.github.guillermodubon.coachgym.notification.web;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryStatus;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Privacy-safe HTTP projection of an operational email delivery. */
@Schema(description = "Operational delivery metadata; recipient and failure details are masked.")
record EmailDeliveryResponse(
        UUID id,
        EmailDeliveryType deliveryType,
        UUID sourceResourceId,
        UUID clientId,
        String maskedRecipient,
        String subject,
        String templateVersion,
        String attachmentFilename,
        String attachmentContentType,
        long attachmentSizeBytes,
        EmailDeliveryStatus status,
        int attemptCount,
        EmailDeliveryFailureCode lastFailureCode,
        Instant requestedAt,
        UUID requestedByUserId,
        Instant sentAt,
        Instant lastAttemptAt,
        Instant createdAt,
        Instant updatedAt,
        long version,
        UUID branchId) {

    static EmailDeliveryResponse from(EmailDeliveryDetails details) {
        return new EmailDeliveryResponse(
                details.id(),
                details.deliveryType(),
                details.sourceResourceId(),
                details.clientId(),
                details.maskedRecipientSnapshot(),
                details.subjectSnapshot(),
                details.templateVersion(),
                details.attachmentFilename(),
                details.attachmentContentType(),
                details.attachmentSizeBytes(),
                details.status(),
                details.attemptCount(),
                details.lastFailureCode(),
                details.requestedAt(),
                details.requestedByUserId(),
                details.sentAt(),
                details.lastAttemptAt(),
                details.createdAt(),
                details.updatedAt(),
                details.version(),
                details.branchId());
    }
}
