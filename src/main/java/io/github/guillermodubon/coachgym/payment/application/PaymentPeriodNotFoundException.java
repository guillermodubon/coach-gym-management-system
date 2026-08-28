package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

public class PaymentPeriodNotFoundException
        extends RuntimeException {

    public PaymentPeriodNotFoundException(UUID membershipPeriodId) {
        super("Membership period " + membershipPeriodId + " was not found.");
    }
}
