package io.github.guillermodubon.coachgym.maintenance.domain;

/** Raised when a maintenance work-order business invariant is violated. */
public class MaintenanceValidationException extends RuntimeException {

    public MaintenanceValidationException(String message) {
        super(message);
    }
}
