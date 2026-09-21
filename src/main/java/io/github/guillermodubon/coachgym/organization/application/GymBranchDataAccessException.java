package io.github.guillermodubon.coachgym.organization.application;

/** Safe boundary exception for unexpected branch persistence failures. */
public class GymBranchDataAccessException extends RuntimeException {

    public GymBranchDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
