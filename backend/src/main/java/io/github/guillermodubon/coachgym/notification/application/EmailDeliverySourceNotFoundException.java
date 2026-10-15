package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import java.util.UUID;

/** Safe failure raised when the requested source resource is not available. */
public class EmailDeliverySourceNotFoundException extends RuntimeException {

    private final EmailDeliveryType deliveryType;
    private final UUID sourceResourceId;

    public EmailDeliverySourceNotFoundException(
            EmailDeliveryType deliveryType, UUID sourceResourceId) {
        super("Email delivery source could not be found.");
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
