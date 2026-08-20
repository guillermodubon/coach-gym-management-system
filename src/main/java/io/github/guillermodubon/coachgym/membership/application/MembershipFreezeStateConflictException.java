package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import java.util.UUID;

public class MembershipFreezeStateConflictException
        extends RuntimeException {

    private final UUID membershipId;
    private final MembershipStatus status;

    public MembershipFreezeStateConflictException(
            UUID membershipId,
            MembershipStatus status) {

        super(
                "Membership "
                        + membershipId
                        + " cannot be frozen while its status is "
                        + status
                        + ".");

        this.membershipId = membershipId;
        this.status = status;
    }

    public UUID membershipId() {
        return membershipId;
    }

    public MembershipStatus status() {
        return status;
    }
}
