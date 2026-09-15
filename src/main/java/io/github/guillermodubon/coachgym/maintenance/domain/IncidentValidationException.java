package io.github.guillermodubon.coachgym.maintenance.domain;

/** Raised when an incident definition or transition violates a domain invariant. */
public final class IncidentValidationException extends RuntimeException {
    public IncidentValidationException(String message) {
        super(message);
    }
}
