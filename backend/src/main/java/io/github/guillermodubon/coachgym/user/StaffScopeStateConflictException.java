package io.github.guillermodubon.coachgym.user;

/** Raised when a scope transition violates a current state invariant. */
public final class StaffScopeStateConflictException extends IllegalStateException {

    public StaffScopeStateConflictException(String message) {
        super(message);
    }
}
