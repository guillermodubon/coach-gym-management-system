package io.github.guillermodubon.coachgym.organization.application;

/** Indicates that the canonical organization is not available. */
public class OrganizationNotFoundException extends RuntimeException {

    public OrganizationNotFoundException() {
        super("The canonical organization was not found.");
    }
}
