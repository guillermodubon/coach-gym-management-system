package io.github.guillermodubon.coachgym.user;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Pure branch-context rules; context selection never expands authorization. */
public final class StaffBranchContextPolicy {

    private StaffBranchContextPolicy() {
    }

    public static boolean canAccessBranch(StaffAuthorizationContext actor, UUID branchId) {
        Objects.requireNonNull(actor, "actor is required");
        Objects.requireNonNull(branchId, "branchId is required");
        if (actor.accountStatus() != StaffAccountStatus.ACTIVE) {
            return false;
        }
        if (actor.scopeType() == StaffScopeType.ORGANIZATION && actor.roles().contains(RoleCode.ADMIN)) {
            return true;
        }
        return actor.scopeType() == StaffScopeType.BRANCH && actor.assignedTo(branchId);
    }

    public static void requireSelectionAllowed(
            StaffAuthorizationContext actor,
            SelectActiveBranchCommand command,
            Set<UUID> activeOrganizationBranchIds) {
        Objects.requireNonNull(actor, "actor is required");
        Objects.requireNonNull(command, "command is required");
        Objects.requireNonNull(activeOrganizationBranchIds, "activeOrganizationBranchIds is required");
        if (!activeOrganizationBranchIds.contains(command.branchId())) {
            throw new StaffScopeStateConflictException("selected branch is not an active branch");
        }
        if (!canAccessBranch(actor, command.branchId())) {
            throw new StaffBranchAuthorizationException("staff member is not assigned to the selected branch");
        }
    }
}
