package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentMethod;

public class DuplicatePaymentReferenceException
        extends RuntimeException {

    public DuplicatePaymentReferenceException(
            PaymentMethod paymentMethod,
            String externalReference) {

        super(
                "A payment with method "
                        + paymentMethod.name()
                        + " and the provided external reference already exists.");
    }
}
