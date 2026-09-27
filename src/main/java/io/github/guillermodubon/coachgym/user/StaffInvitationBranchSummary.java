package io.github.guillermodubon.coachgym.user;

import java.util.UUID;

/** Minimal branch label safe to show to the person reviewing an invitation. */
public record StaffInvitationBranchSummary(UUID branchId, String code, String name) {

    public StaffInvitationBranchSummary {
        if (branchId == null) {
            throw new StaffIdentityValidationException("Branch identity is required.");
        }
        code = StaffAssignmentValuePolicy.requireCode(code, "branch code");
        name = StaffAssignmentValuePolicy.requireText(name, "branch name", 160);
    }
}
