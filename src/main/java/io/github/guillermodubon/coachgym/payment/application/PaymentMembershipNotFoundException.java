package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

public class PaymentMembershipNotFoundException
        extends RuntimeException {

    public PaymentMembershipNotFoundException(UUID membershipId) {
        super("Membership " + membershipId + " was not found.");
    }
}
