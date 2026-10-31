package io.github.guillermodubon.coachgym.organization.application;

/** The current staff authority could not be verified. */
public final class OrganizationAdministrationUnavailableException extends RuntimeException {

    public OrganizationAdministrationUnavailableException(Throwable cause) {
        super("Administrative authority could not be verified.", cause);
    }
}
