package io.github.guillermodubon.coachgym.audit;

/** Raised when an audit query or public projection violates its contract. */
public final class AuditQueryValidationException extends IllegalArgumentException {

    public AuditQueryValidationException(String message) {
        super(message);
    }
}
