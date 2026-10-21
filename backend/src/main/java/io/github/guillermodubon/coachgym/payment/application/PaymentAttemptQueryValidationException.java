package io.github.guillermodubon.coachgym.payment.application;

/** Invalid bounded payment-attempt search parameters. */
public class PaymentAttemptQueryValidationException extends RuntimeException {

    public PaymentAttemptQueryValidationException(String message) {
        super(message);
    }
}
