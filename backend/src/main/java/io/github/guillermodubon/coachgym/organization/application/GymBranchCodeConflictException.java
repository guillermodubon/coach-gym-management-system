package io.github.guillermodubon.coachgym.organization.application;

/** Indicates that a canonical branch code is already in use. */
public class GymBranchCodeConflictException extends RuntimeException {

    public GymBranchCodeConflictException() {
        super("The gym branch code is already in use.");
    }
}
