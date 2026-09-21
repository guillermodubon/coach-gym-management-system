package io.github.guillermodubon.coachgym.organization.application;

/** Indicates that a branch update used a stale version. */
public class GymBranchVersionConflictException extends RuntimeException {

    public GymBranchVersionConflictException() {
        super("The gym branch was modified by another operation. Reload it and try again.");
    }
}
