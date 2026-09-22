package io.github.guillermodubon.coachgym.user;

/** Raised when an assignment command or value is invalid. */
public final class StaffBranchAssignmentValidationException extends IllegalArgumentException {

    public StaffBranchAssignmentValidationException(String message) {
        super(message);
    }
}
