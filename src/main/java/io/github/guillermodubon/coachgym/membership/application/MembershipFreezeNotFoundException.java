package io.github.guillermodubon.coachgym.membership.application;

import java.util.UUID;

public class MembershipFreezeNotFoundException
        extends RuntimeException {

    private final UUID membershipId;

    public MembershipFreezeNotFoundException(
            UUID membershipId) {

        super(
                "An open freeze for membership "
                        + membershipId
                        + " was not found.");

        this.membershipId = membershipId;
    }

    public UUID membershipId() {
        return membershipId;
    }
}