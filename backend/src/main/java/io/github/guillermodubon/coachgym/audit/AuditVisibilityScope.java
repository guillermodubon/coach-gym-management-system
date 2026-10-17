package io.github.guillermodubon.coachgym.audit;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Server-resolved visibility passed to audit persistence queries.
 *
 * <p>An organization-wide scope includes global and historically unattributed
 * entries. A branch scope contains only explicit branch snapshots and never
 * includes entries without a persisted branch identifier. This contract does
 * not authorize a caller; the application layer must construct it only after
 * resolving current staff scope and branch authority.</p>
 */
public record AuditVisibilityScope(boolean organizationWide, Set<UUID> branchIds) {

    public AuditVisibilityScope {
        Objects.requireNonNull(branchIds, "branchIds is required.");
        if (branchIds.stream().anyMatch(Objects::isNull)) {
            throw new AuditQueryValidationException(
                    "Audit visibility branch identifiers are invalid.");
        }
        if (organizationWide && !branchIds.isEmpty()) {
            throw new AuditQueryValidationException(
                    "Organization-wide audit visibility cannot include branch filters.");
        }
        if (!organizationWide && branchIds.isEmpty()) {
            throw new AuditQueryValidationException(
                    "Branch-scoped audit visibility requires an explicit branch.");
        }
        if (branchIds.size() > AuditQueryPolicy.MAX_BRANCH_IDS) {
            throw new AuditQueryValidationException(
                    "Audit visibility cannot include more than 100 branches.");
        }
        branchIds = Collections.unmodifiableSet(new TreeSet<>(branchIds));
    }

    public static AuditVisibilityScope organization() {
        return new AuditVisibilityScope(true, Set.of());
    }

    public static AuditVisibilityScope branches(Set<UUID> branchIds) {
        return new AuditVisibilityScope(false, branchIds);
    }
}
