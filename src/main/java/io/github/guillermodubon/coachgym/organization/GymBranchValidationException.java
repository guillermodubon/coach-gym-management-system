package io.github.guillermodubon.coachgym.organization;

/** Safe validation failure for gym-branch contracts. */
public final class GymBranchValidationException extends IllegalArgumentException {

    public GymBranchValidationException(String message) {
        super(message);
    }
}
