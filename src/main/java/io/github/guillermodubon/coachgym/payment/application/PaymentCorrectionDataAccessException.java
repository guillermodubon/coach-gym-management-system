package io.github.guillermodubon.coachgym.payment.application;

/** Safe application exception for payment correction persistence failures. */
public class PaymentCorrectionDataAccessException extends RuntimeException {

    public PaymentCorrectionDataAccessException(
            String message,
            Throwable cause) {
        super(message, cause);
    }
}
