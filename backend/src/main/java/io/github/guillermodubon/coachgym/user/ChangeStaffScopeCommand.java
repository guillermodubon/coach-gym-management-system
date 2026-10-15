package io.github.guillermodubon.coachgym.user;

import java.util.Objects;
import java.util.UUID;

/** Request to change a staff member's single organizational scope. */
public record ChangeStaffScopeCommand(
        UUID targetUserId,
        StaffScopeType requestedScope,
        String reason,
        long expectedVersion) {

    public ChangeStaffScopeCommand {
        targetUserId = StaffAssignmentValuePolicy.requireId(targetUserId, "targetUserId");
        requestedScope = Objects.requireNonNull(requestedScope, "requestedScope is required");
        reason = StaffAssignmentValuePolicy.requireReason(reason);
        StaffAssignmentValuePolicy.requireVersion(expectedVersion, "expectedVersion");
    }
}
