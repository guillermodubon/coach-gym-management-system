package io.github.guillermodubon.coachgym.plan.application;

public class PlanVersionConflictException extends RuntimeException {

    public PlanVersionConflictException() {
        super("The plan was modified by another operation. Reload it and try again.");
    }
}
