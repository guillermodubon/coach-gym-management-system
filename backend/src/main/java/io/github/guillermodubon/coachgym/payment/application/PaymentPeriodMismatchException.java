package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

public class PaymentPeriodMismatchException
        extends RuntimeException {

    public PaymentPeriodMismatchException(
            UUID membershipId,
            UUID membershipPeriodId) {

        super(
                "Membership period "
                        + membershipPeriodId
                        + " does not belong to membership "
                        + membershipId
                        + ".");
    }
}
