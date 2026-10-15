package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptDetails;

/** Internal application projection containing the reference needed for provider cancellation. */
public record PaymentAttemptProviderDetails(
        PaymentAttemptDetails details,
        String checkoutReference) {

    public PaymentAttemptProviderDetails {
        if (details == null) {
            throw new IllegalArgumentException("Payment attempt details are required.");
        }
        if (checkoutReference != null) {
            if (checkoutReference.isBlank()) {
                throw new IllegalArgumentException("Checkout reference must not be blank.");
            }
            checkoutReference = checkoutReference.strip();
        }
    }
}
