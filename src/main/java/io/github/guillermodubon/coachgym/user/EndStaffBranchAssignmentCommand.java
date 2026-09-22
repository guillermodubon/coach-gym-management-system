package io.github.guillermodubon.coachgym.user;

import java.util.UUID;

/** Request to end an active assignment using optimistic concurrency. */
public record EndStaffBranchAssignmentCommand(UUID assignmentId, String reason, long expectedVersion) {

    public EndStaffBranchAssignmentCommand {
        assignmentId = StaffAssignmentValuePolicy.requireId(assignmentId, "assignmentId");
        reason = StaffAssignmentValuePolicy.requireReason(reason);
        StaffAssignmentValuePolicy.requireVersion(expectedVersion, "expectedVersion");
    }
}
