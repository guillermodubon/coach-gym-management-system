package io.github.guillermodubon.coachgym.organization;

/** Safe validation failure for organization contracts. */
public final class OrganizationValidationException extends IllegalArgumentException {

    public OrganizationValidationException(String message) {
        super(message);
    }
}
