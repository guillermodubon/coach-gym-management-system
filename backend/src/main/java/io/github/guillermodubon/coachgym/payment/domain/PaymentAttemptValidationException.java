package io.github.guillermodubon.coachgym.payment.domain;

/** Raised when a payment-attempt value object violates a domain invariant. */
public class PaymentAttemptValidationException extends RuntimeException {

    public PaymentAttemptValidationException(String message) {
        super(message);
    }
}
