package io.github.guillermodubon.coachgym.plan.application;

import java.util.UUID;

public class PlanNotFoundException extends RuntimeException {

    public PlanNotFoundException(UUID id) {
        super("Plan " + id + " was not found.");
    }
}
