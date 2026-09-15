package io.github.guillermodubon.coachgym.payment.domain;

/**
 * Raised when the public manual payment flow attempts to create a CARD row.
 * Card payments must be materialized only from a verified provider outcome.
 */
public class ManualCardPaymentNotAllowedException extends RuntimeException {

    public ManualCardPaymentNotAllowedException() {
        super("Manual CARD payment registration is not allowed; use the verified provider flow.");
    }
}
