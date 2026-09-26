package io.github.guillermodubon.coachgym.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public read port for safe active branches available to one staff account. */
public interface AuthorizedBranchQuery {

    Optional<UUID> findAuthorizedOrganizationId(UUID userId);

    List<AuthorizedBranchSummary> findAuthorizedActiveBranches(UUID userId);

    /** Holds the active branch and (for branch scope) assignment against concurrent deactivation. */
    boolean lockAuthorizedActiveBranchForOperation(
            UUID userId, UUID branchId, StaffScopeType scopeType);
}
