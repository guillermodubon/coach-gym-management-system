package io.github.guillermodubon.coachgym.membership.application;

import java.util.UUID;

public class MembershipAlreadyCancelledException
        extends RuntimeException {

    private final UUID membershipId;

    public MembershipAlreadyCancelledException(
            UUID membershipId) {

        super(
                "Membership "
                        + membershipId
                        + " is already cancelled.");

        this.membershipId =
                membershipId;
    }

    public UUID membershipId() {
        return membershipId;
    }
}
