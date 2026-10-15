package io.github.guillermodubon.coachgym.membership.application;

import java.util.UUID;

public class MembershipPlanNotAvailableException
        extends RuntimeException {

    public MembershipPlanNotAvailableException(
            UUID membershipPlanId) {

        super(
                "Membership plan "
                        + membershipPlanId
                        + " was not found or is inactive.");
    }
}
