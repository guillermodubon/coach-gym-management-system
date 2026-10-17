package io.github.guillermodubon.coachgym.user;

import java.util.UUID;

/** Request to create one active staff-to-branch assignment. */
public record AssignStaffToBranchCommand(UUID targetUserId, UUID branchId, String reason) {

    public AssignStaffToBranchCommand {
        targetUserId = StaffAssignmentValuePolicy.requireId(targetUserId, "targetUserId");
        branchId = StaffAssignmentValuePolicy.requireId(branchId, "branchId");
        reason = StaffAssignmentValuePolicy.requireReason(reason);
    }
}
