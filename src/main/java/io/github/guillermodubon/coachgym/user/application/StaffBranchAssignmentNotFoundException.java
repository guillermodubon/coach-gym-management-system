package io.github.guillermodubon.coachgym.user.application;

/** Indicates that a staff branch assignment is not available. */
public class StaffBranchAssignmentNotFoundException extends RuntimeException {

    public StaffBranchAssignmentNotFoundException() {
        super("The staff branch assignment was not found.");
    }
}
