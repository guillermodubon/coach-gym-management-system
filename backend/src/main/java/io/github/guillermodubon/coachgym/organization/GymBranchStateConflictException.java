package io.github.guillermodubon.coachgym.organization;

/** Indicates that a gym-branch lifecycle transition is not allowed. */
public final class GymBranchStateConflictException extends RuntimeException {

    public GymBranchStateConflictException(String message) {
        super(message);
    }
}
