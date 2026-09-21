package io.github.guillermodubon.coachgym.organization.application;

/** Safe boundary exception for unexpected organization persistence failures. */
public class OrganizationDataAccessException extends RuntimeException {

    public OrganizationDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
