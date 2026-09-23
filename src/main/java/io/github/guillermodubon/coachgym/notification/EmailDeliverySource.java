package io.github.guillermodubon.coachgym.notification;

import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValuePolicy;
import java.util.Objects;
import java.util.UUID;

/** Resolved, server-owned source data used by later message orchestration. */
public record EmailDeliverySource(
        EmailDeliveryType deliveryType,
        UUID sourceResourceId,
        UUID clientId,
        String recipient,
        EmailAttachment attachment,
        EmailDeliveryTemplateData templateData,
        UUID branchId) {

    public EmailDeliverySource(
            EmailDeliveryType deliveryType,
            UUID sourceResourceId,
            UUID clientId,
            String recipient,
            EmailAttachment attachment) {
        this(deliveryType, sourceResourceId, clientId, recipient, attachment,
                EmailDeliveryTemplateData.empty());
    }

    public EmailDeliverySource(
            EmailDeliveryType deliveryType,
            UUID sourceResourceId,
            UUID clientId,
            String recipient,
            EmailAttachment attachment,
            EmailDeliveryTemplateData templateData) {
        this(deliveryType, sourceResourceId, clientId, recipient, attachment,
                templateData, null);
    }

    public EmailDeliverySource {
        deliveryType = Objects.requireNonNull(deliveryType, "Delivery type is required.");
        sourceResourceId = Objects.requireNonNull(sourceResourceId, "Source id is required.");
        clientId = Objects.requireNonNull(clientId, "Client id is required.");
        recipient = EmailDeliveryValuePolicy.normalizeRecipient(recipient);
        attachment = Objects.requireNonNull(attachment, "Email attachment is required.");
        templateData = Objects.requireNonNull(templateData, "Email template data is required.");
        String expectedType = deliveryType == EmailDeliveryType.PAYMENT_RECEIPT
                ? "application/pdf" : "image/png";
        if (!expectedType.equals(attachment.contentType())) {
            throw new IllegalArgumentException("Attachment type does not match the delivery type.");
        }
    }

    @Override
    public String toString() {
        return "EmailDeliverySource[deliveryType=" + deliveryType
                + ", sourceResourceId=" + sourceResourceId
                + ", clientId=" + clientId
                + ", recipient=" + EmailDeliveryValuePolicy.maskRecipient(recipient)
                + ", attachment=" + attachment
                + ']';
    }
}
