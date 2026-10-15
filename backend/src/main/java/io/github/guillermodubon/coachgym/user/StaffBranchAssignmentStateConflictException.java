package io.github.guillermodubon.coachgym.user;

/** Raised when an assignment cannot perform the requested lifecycle transition. */
public final class StaffBranchAssignmentStateConflictException extends IllegalStateException {

    public StaffBranchAssignmentStateConflictException(String message) {
        super(message);
    }
}
