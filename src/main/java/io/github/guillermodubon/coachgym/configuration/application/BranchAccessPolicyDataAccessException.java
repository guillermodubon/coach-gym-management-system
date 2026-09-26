package io.github.guillermodubon.coachgym.configuration.application;

/** Safe boundary exception for branch access-policy persistence failures. */
public class BranchAccessPolicyDataAccessException extends RuntimeException {

    public BranchAccessPolicyDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
