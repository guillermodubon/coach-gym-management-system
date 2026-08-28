package io.github.guillermodubon.coachgym.payment.domain;

import java.util.UUID;

public class PaymentMembershipMismatchException
        extends RuntimeException {

    public PaymentMembershipMismatchException(
            UUID clientId,
            UUID membershipId) {

        super(
                "Membership "
                        + membershipId
                        + " does not belong to client "
                        + clientId
                        + ".");
    }
}
