package io.github.guillermodubon.coachgym.audit;

import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pure branch-scope policy for audit query and export authorization.
 *
 * <p>A null or missing branch in persisted audit metadata denotes a global or
 * unattributed entry. Such entries may only be included in an organization
 * administrator's organization-wide query. Branch administrators must supply
 * a non-empty, explicit set fully contained in their active assignments;
 * filtering and detail re-authorization remain application responsibilities.
 * CSV exports use this same visibility policy and apply the resolved branch
 * predicate in SQL before opening the streaming result set.</p>
 */
public final class AuditBranchVisibilityPolicy {

    private AuditBranchVisibilityPolicy() {
    }

    public static boolean allowsQuery(
            StaffAuthorizationContext actor,
            boolean organizationWide,
            Set<UUID> branchIds) {
        return allows(actor, organizationWide, branchIds);
    }

    public static boolean allowsCsvExport(
            StaffAuthorizationContext actor,
            boolean organizationWide,
            Set<UUID> branchIds) {
        return allows(actor, organizationWide, branchIds);
    }

    private static boolean allows(
            StaffAuthorizationContext actor,
            boolean organizationWide,
            Set<UUID> branchIds) {
        if (actor == null || branchIds == null
                || branchIds.stream().anyMatch(Objects::isNull)) {
            return false;
        }
        if (organizationWide != branchIds.isEmpty()) {
            return false;
        }
        if (actor.organizationAdmin()) {
            return true;
        }
        return actor.branchAdmin()
                && !organizationWide
                && actor.assignedBranchIds().containsAll(branchIds);
    }
}
