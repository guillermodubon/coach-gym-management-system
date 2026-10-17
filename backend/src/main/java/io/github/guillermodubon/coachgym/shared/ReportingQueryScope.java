package io.github.guillermodubon.coachgym.shared;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Explicit SQL filter for organization-wide or branch-filtered report reads.
 *
 * <p>This value does not authorize its branch identifiers. The caller must
 * derive a branch-filtered scope from the authenticated actor's validated
 * reporting policy before invoking a reporting query.</p>
 */
public record ReportingQueryScope(boolean organizationWide, List<UUID> branchIds) {

    public ReportingQueryScope {
        Objects.requireNonNull(branchIds, "Report branch identifiers are required.");
        if (branchIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Report branch identifiers must not contain null.");
        }
        branchIds = List.copyOf(new TreeSet<>(branchIds));
        if (organizationWide == !branchIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Organization-wide scope must have no branch filters; branch scope must have at least one.");
        }
    }

    public static ReportingQueryScope organization() {
        return new ReportingQueryScope(true, List.of());
    }

    public static ReportingQueryScope branches(Collection<UUID> branchIds) {
        Objects.requireNonNull(branchIds, "Report branch identifiers are required.");
        return new ReportingQueryScope(false, List.copyOf(branchIds));
    }
}
