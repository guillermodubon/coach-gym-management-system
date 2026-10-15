package io.github.guillermodubon.coachgym.organization;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Public, bounded branch-metadata query for authorized reporting composition.
 *
 * <p>The query returns only canonical-organization branches whose identifiers
 * were explicitly supplied. The caller remains responsible for authorizing
 * each identifier before using the returned metadata.</p>
 */
public interface GymBranchReportingQuery {

    int MAXIMUM_BRANCH_IDS = 100;

    List<GymBranchSummary> findCanonicalBranches(
            Collection<UUID> branchIds,
            boolean includeInactive);
}
