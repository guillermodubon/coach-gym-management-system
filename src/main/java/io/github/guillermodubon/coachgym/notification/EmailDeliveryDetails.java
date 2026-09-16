package io.github.guillermodubon.coachgym.notification;

import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValuePolicy;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable operational snapshot of one canonical logical email delivery.
 *
 * <p>The recipient is retained as a server-owned snapshot for a future retry;
 * normal HTTP projections must use {@link #maskedRecipientSnapshot()}.</p>
 */
public record EmailDeliveryDetails(
        UUID id,
        EmailDeliveryType deliveryType,
        UUID sourceResourceId,
        UUID clientId,
        String recipientSnapshot,
        String subjectSnapshot,
        String templateVersion,
        String attachmentResourceType,
        UUID attachmentResourceId,
        String attachmentFilename,
        String attachmentContentType,
        long attachmentSizeBytes,
        String attachmentChecksumSha256,
        String idempotencyKeyDigest,
        EmailDeliveryStatus status,
        int attemptCount,
        EmailDeliveryFailureCode lastFailureCode,
        String lastFailureMessage,
        Instant requestedAt,
        UUID requestedByUserId,
        Instant sentAt,
        Instant lastAttemptAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    private static final int MAX_RESOURCE_TYPE_LENGTH = 32;

    public EmailDeliveryDetails {
        id = required(id, "Email delivery id");
        if (deliveryType == null) {
            throw invalid("Email delivery type is required.");
        }
        sourceResourceId = required(sourceResourceId, "Email source resource id");
        clientId = required(clientId, "Email client id");
        recipientSnapshot = EmailDeliveryValuePolicy.normalizeRecipient(recipientSnapshot);
        subjectSnapshot = EmailDeliveryValuePolicy.normalizeSubject(subjectSnapshot);
        templateVersion = EmailDeliveryValuePolicy.normalizeTemplateVersion(templateVersion);
        attachmentResourceType = requiredText(
                attachmentResourceType, "Email attachment resource type", MAX_RESOURCE_TYPE_LENGTH);
        if (!deliveryType.name().equals(attachmentResourceType)) {
            throw invalid("Email attachment resource type must match the delivery type.");
        }
        attachmentResourceId = required(attachmentResourceId, "Email attachment resource id");
        attachmentFilename = EmailDeliveryValuePolicy.normalizeFilename(attachmentFilename);
        attachmentContentType = normalizeAttachmentContentType(attachmentContentType, deliveryType);
        if (attachmentSizeBytes < 1
                || attachmentSizeBytes > EmailDeliveryValuePolicy.MAX_ATTACHMENT_BYTES) {
            throw invalid("Email attachment size is outside the allowed bounds.");
        }
        attachmentChecksumSha256 = EmailDeliveryValuePolicy.normalizeChecksum(
                attachmentChecksumSha256, "Email attachment checksum");
        idempotencyKeyDigest = EmailDeliveryValuePolicy.normalizeDigest(idempotencyKeyDigest);
        if (status == null) {
            throw invalid("Email delivery status is required.");
        }
        if (attemptCount < 0) {
            throw invalid("Email delivery attempt count must not be negative.");
        }
        if (status != EmailDeliveryStatus.PENDING && attemptCount < 1) {
            throw invalid("Completed email delivery must contain an attempt.");
        }
        if (status == EmailDeliveryStatus.FAILED && lastFailureMessage != null) {
            lastFailureMessage = EmailDeliveryValuePolicy.normalizeFailureMessage(lastFailureMessage);
        }
        validateStatusMetadata(status, sentAt, lastFailureCode, lastFailureMessage);
        requestedAt = required(requestedAt, "Email requested timestamp");
        requestedByUserId = required(requestedByUserId, "Email requesting actor id");
        if (status != EmailDeliveryStatus.PENDING && lastAttemptAt == null) {
            throw invalid("Completed email delivery requires an attempt timestamp.");
        }
        if (lastAttemptAt != null && lastAttemptAt.isBefore(requestedAt)) {
            throw invalid("Email last-attempt timestamp cannot precede the request.");
        }
        if (sentAt != null && sentAt.isBefore(requestedAt)) {
            throw invalid("Email sent timestamp cannot precede the request.");
        }
        createdAt = required(createdAt, "Email created timestamp");
        updatedAt = required(updatedAt, "Email updated timestamp");
        if (createdAt.isBefore(requestedAt)) {
            throw invalid("Email creation timestamp cannot precede the request.");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw invalid("Email updated timestamp cannot precede creation.");
        }
        if (version < 0) {
            throw invalid("Email delivery version must not be negative.");
        }
    }

    /** Returns the privacy-safe recipient representation for normal APIs/audit. */
    public String maskedRecipientSnapshot() {
        return EmailDeliveryValuePolicy.maskRecipient(recipientSnapshot);
    }

    @Override
    public String toString() {
        return "EmailDeliveryDetails[id=" + id
                + ", deliveryType=" + deliveryType
                + ", sourceResourceId=" + sourceResourceId
                + ", clientId=" + clientId
                + ", recipient=" + maskedRecipientSnapshot()
                + ", subjectPresent=true"
                + ", templateVersion=" + templateVersion
                + ", attachmentFilename=" + attachmentFilename
                + ", attachmentContentType=" + attachmentContentType
                + ", attachmentSizeBytes=" + attachmentSizeBytes
                + ", attachmentChecksumPresent=true"
                + ", idempotencyKeyPresent=true"
                + ", status=" + status
                + ", attemptCount=" + attemptCount
                + ", lastFailureCode=" + lastFailureCode
                + ", lastFailureMessagePresent=" + (lastFailureMessage != null)
                + ", requestedAt=" + requestedAt
                + ", requestedByUserId=" + requestedByUserId
                + ", sentAt=" + sentAt
                + ", lastAttemptAt=" + lastAttemptAt
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + ", version=" + version
                + ']';
    }

    private static void validateStatusMetadata(
            EmailDeliveryStatus status,
            Instant sentAt,
            EmailDeliveryFailureCode failureCode,
            String failureMessage) {
        switch (status) {
            case PENDING -> {
                if (sentAt != null || failureCode != null || failureMessage != null) {
                    throw invalid("Pending email delivery cannot contain completion metadata.");
                }
            }
            case SENT -> {
                if (sentAt == null || failureCode != null || failureMessage != null) {
                    throw invalid("Sent email delivery requires only sent metadata.");
                }
            }
            case FAILED -> {
                if (sentAt != null || failureCode == null || failureMessage == null) {
                    throw invalid("Failed email delivery requires safe failure metadata.");
                }
            }
        }
    }

    private static String normalizeAttachmentContentType(
            String value, EmailDeliveryType type) {
        if (value == null || value.isBlank()) {
            throw invalid("Email attachment content type is required.");
        }
        String normalized = value.strip().toLowerCase(java.util.Locale.ROOT);
        String expected = type == EmailDeliveryType.PAYMENT_RECEIPT
                ? "application/pdf" : "image/png";
        if (!expected.equals(normalized)) {
            throw invalid("Email attachment content type does not match the delivery type.");
        }
        return normalized;
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " is required.");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw invalid(field + " must not exceed " + maxLength + " characters.");
        }
        return normalized;
    }

    private static UUID required(UUID value, String field) {
        if (value == null) {
            throw invalid(field + " is required.");
        }
        return value;
    }

    private static Instant required(Instant value, String field) {
        if (value == null) {
            throw invalid(field + " is required.");
        }
        return value;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
