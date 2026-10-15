package io.github.guillermodubon.coachgym.promotion.application;

import java.util.UUID;

public class EligiblePlanNotFoundException
        extends RuntimeException {

    public EligiblePlanNotFoundException(
            UUID planId) {

        super(
                "Membership plan "
                        + planId
                        + " was not found.");
    }
}
