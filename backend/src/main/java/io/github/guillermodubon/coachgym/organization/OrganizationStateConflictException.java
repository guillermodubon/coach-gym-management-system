package io.github.guillermodubon.coachgym.organization;

/** Indicates that an organization lifecycle transition is not allowed. */
public final class OrganizationStateConflictException extends RuntimeException {

    public OrganizationStateConflictException(String message) {
        super(message);
    }
}
