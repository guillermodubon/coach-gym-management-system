package io.github.guillermodubon.coachgym.payment.application;

/** Indicates invalid receipt-generation input or contract data. */
public class PaymentReceiptValidationException extends RuntimeException {

    public PaymentReceiptValidationException(String message) {
        super(message);
    }
}
