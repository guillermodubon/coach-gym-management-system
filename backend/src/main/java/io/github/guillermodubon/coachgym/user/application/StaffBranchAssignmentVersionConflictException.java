package io.github.guillermodubon.coachgym.user.application;

import java.util.UUID;

/** Indicates that an assignment update used a stale version. */
public class StaffBranchAssignmentVersionConflictException extends RuntimeException {

    public StaffBranchAssignmentVersionConflictException(UUID assignmentId) {
        super("The staff branch assignment was modified by another operation. Reload it and try again.");
    }
}
