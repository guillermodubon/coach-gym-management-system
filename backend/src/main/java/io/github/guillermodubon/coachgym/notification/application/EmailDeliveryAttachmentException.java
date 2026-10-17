package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import java.util.UUID;

/** Safe failure raised when a canonical attachment is missing or invalid. */
public class EmailDeliveryAttachmentException extends RuntimeException {

    private final EmailDeliveryType deliveryType;
    private final UUID sourceResourceId;

    public EmailDeliveryAttachmentException(
            EmailDeliveryType deliveryType, UUID sourceResourceId, String message) {
        super(message);
        this.deliveryType = deliveryType;
        this.sourceResourceId = sourceResourceId;
    }

    public EmailDeliveryType deliveryType() {
        return deliveryType;
    }

    public UUID sourceResourceId() {
        return sourceResourceId;
    }
}
