package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

public class PaymentNotFoundException
        extends RuntimeException {

    public PaymentNotFoundException(UUID paymentId) {
        super("Payment " + paymentId + " was not found.");
    }
}
