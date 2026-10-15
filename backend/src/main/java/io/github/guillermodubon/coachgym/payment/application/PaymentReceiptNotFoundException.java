package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

/** Indicates that the requested receipt resource does not exist. */
public class PaymentReceiptNotFoundException extends RuntimeException {

    private final UUID resourceId;

    public PaymentReceiptNotFoundException(UUID resourceId) {
        super("Payment receipt was not found.");
        if (resourceId == null) {
            throw new PaymentReceiptValidationException("Receipt resource id is required.");
        }
        this.resourceId = resourceId;
    }

    public UUID resourceId() {
        return resourceId;
    }
}
