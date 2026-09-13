package io.github.guillermodubon.coachgym.payment.application;

/** Safe application exception for receipt persistence failures. */
public class PaymentReceiptDataAccessException extends RuntimeException {

    public PaymentReceiptDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
