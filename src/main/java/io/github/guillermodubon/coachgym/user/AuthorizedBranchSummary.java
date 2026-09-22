package io.github.guillermodubon.coachgym.user;

import java.util.Objects;
import java.util.UUID;

/** Minimal safe branch projection for a staff member's branch context. */
public record AuthorizedBranchSummary(
        UUID id,
        UUID organizationId,
        String code,
        String name,
        String timezone,
        boolean initialBranch) {

    public AuthorizedBranchSummary {
        id = StaffAssignmentValuePolicy.requireId(id, "id");
        organizationId = StaffAssignmentValuePolicy.requireId(organizationId, "organizationId");
        code = StaffAssignmentValuePolicy.requireCode(code, "code");
        name = StaffAssignmentValuePolicy.requireText(name, "name", 160);
        timezone = StaffAssignmentValuePolicy.requireTimezone(timezone);
    }
}
