package io.github.guillermodubon.coachgym.user.application;

/** Indicates that the requested active assignment already exists. */
public class StaffBranchAssignmentDuplicateException extends RuntimeException {

    public StaffBranchAssignmentDuplicateException() {
        super("The active staff branch assignment already exists.");
    }
}
