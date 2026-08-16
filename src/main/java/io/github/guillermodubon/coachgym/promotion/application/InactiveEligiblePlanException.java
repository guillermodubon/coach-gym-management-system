package io.github.guillermodubon.coachgym.promotion.application;

import java.util.UUID;

public class InactiveEligiblePlanException
        extends RuntimeException {

    public InactiveEligiblePlanException(
            UUID planId) {

        super(
                "Membership plan "
                        + planId
                        + " is inactive and cannot be assigned "
                        + "to a promotion.");
    }
}
