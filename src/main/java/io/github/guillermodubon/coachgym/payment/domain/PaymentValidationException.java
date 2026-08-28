package io.github.guillermodubon.coachgym.payment.domain;

public class PaymentValidationException
        extends RuntimeException {

    public PaymentValidationException(
            String message) {

        super(message);
    }
}
