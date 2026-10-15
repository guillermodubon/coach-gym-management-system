package io.github.guillermodubon.coachgym.membership.application;

import java.util.UUID;

public class MembershipAlreadyFrozenException
        extends RuntimeException {

    private final UUID membershipId;

    public MembershipAlreadyFrozenException(
            UUID membershipId) {

        super(
                "Membership "
                        + membershipId
                        + " is already frozen.");

        this.membershipId = membershipId;
    }

    public UUID membershipId() {
        return membershipId;
    }
}
