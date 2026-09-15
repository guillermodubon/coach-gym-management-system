package io.github.guillermodubon.coachgym.payment.application;

/** Safe application exception for confirmed-payment lookup failures. */
public class ConfirmedPaymentForAccessDataAccessException
        extends RuntimeException {

    public ConfirmedPaymentForAccessDataAccessException(
            String message,
            Throwable cause) {
        super(message, cause);
    }
}
