package io.github.guillermodubon.coachgym.payment.domain;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import java.util.UUID;

public class PaymentMembershipStateConflictException
        extends RuntimeException {

    public PaymentMembershipStateConflictException(
            UUID membershipId,
            MembershipStatus status) {

        super(
                "Payment cannot be registered for membership "
                        + membershipId
                        + " in status "
                        + status.name()
                        + ".");
    }
}
