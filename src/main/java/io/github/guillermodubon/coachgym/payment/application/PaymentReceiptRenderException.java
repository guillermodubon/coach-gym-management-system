package io.github.guillermodubon.coachgym.payment.application;

/** Safe application exception for receipt document rendering failures. */
public class PaymentReceiptRenderException extends RuntimeException {

    public PaymentReceiptRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
