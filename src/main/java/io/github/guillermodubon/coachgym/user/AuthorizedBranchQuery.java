package io.github.guillermodubon.coachgym.user;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Public read port for safe active branches available to one staff account. */
public interface AuthorizedBranchQuery {

    Optional<UUID> findAuthorizedOrganizationId(UUID userId);

    List<AuthorizedBranchSummary> findAuthorizedActiveBranches(UUID userId);

    /** Returns only active canonical branches captured by one invitation proposal. */
    List<AuthorizedBranchSummary> findActiveInvitationBranches(
            UUID organizationId, Set<UUID> branchIds);

    /** Locks proposed active branches against concurrent lifecycle changes until transaction end. */
    List<AuthorizedBranchSummary> lockActiveInvitationBranches(
            UUID organizationId, Set<UUID> branchIds);

    /** Holds the active branch and (for branch scope) assignment against concurrent deactivation. */
    boolean lockAuthorizedActiveBranchForOperation(
            UUID userId, UUID branchId, StaffScopeType scopeType);
}
