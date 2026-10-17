package io.github.guillermodubon.coachgym.payment.application;

/** Safe application exception for receipt document storage failures. */
public class PaymentReceiptStorageException extends RuntimeException {

    public PaymentReceiptStorageException(String message) {
        super(message);
    }

    public PaymentReceiptStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
