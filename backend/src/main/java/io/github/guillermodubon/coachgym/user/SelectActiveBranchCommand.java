package io.github.guillermodubon.coachgym.user;

import java.util.UUID;

/** Request to select a branch context; selection never grants authority. */
public record SelectActiveBranchCommand(UUID branchId) {

    public SelectActiveBranchCommand {
        branchId = StaffAssignmentValuePolicy.requireId(branchId, "branchId");
    }
}
