package io.github.guillermodubon.coachgym.user;

/** Raised when a staff actor lacks authority for a branch operation. */
public final class StaffBranchAuthorizationException extends IllegalStateException {

    public StaffBranchAuthorizationException(String message) {
        super(message);
    }
}
