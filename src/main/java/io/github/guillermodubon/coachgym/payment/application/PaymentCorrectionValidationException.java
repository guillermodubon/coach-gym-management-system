package io.github.guillermodubon.coachgym.payment.application;

/** Indicates invalid user-provided payment correction data. */
public class PaymentCorrectionValidationException extends RuntimeException {

    public PaymentCorrectionValidationException(String message) {
        super(message);
    }
}
