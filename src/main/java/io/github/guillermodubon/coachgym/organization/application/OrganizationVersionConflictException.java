package io.github.guillermodubon.coachgym.organization.application;

/** Indicates that an organization update used a stale version. */
public class OrganizationVersionConflictException extends RuntimeException {

    public OrganizationVersionConflictException() {
        super("The organization was modified by another operation. Reload it and try again.");
    }
}
