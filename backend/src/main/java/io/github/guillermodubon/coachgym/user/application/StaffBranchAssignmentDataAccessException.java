package io.github.guillermodubon.coachgym.user.application;

/** Safe boundary exception for unexpected assignment persistence failures. */
public class StaffBranchAssignmentDataAccessException extends RuntimeException {

    public StaffBranchAssignmentDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
