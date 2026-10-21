package io.github.guillermodubon.coachgym.payment.application;

/** Safe boundary for an unavailable payment-attempt read query. */
public class PaymentAttemptQueryUnavailableException extends RuntimeException {

    public PaymentAttemptQueryUnavailableException(Throwable cause) {
        super("Payment-attempt query is unavailable.", cause);
    }
}
