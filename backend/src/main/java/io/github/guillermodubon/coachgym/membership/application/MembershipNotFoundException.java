package io.github.guillermodubon.coachgym.membership.application;

import java.util.UUID;

public class MembershipNotFoundException
        extends RuntimeException {

    public MembershipNotFoundException(
            UUID membershipId) {

        super(
                "Membership "
                        + membershipId
                        + " was not found.");
    }
}
