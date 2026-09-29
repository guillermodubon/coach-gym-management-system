package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.audit.AuditBranchVisibilityPolicy;
import io.github.guillermodubon.coachgym.audit.AuditQueryPolicy;
import io.github.guillermodubon.coachgym.audit.AuditVisibilityScope;
import io.github.guillermodubon.coachgym.organization.GymBranchReportingQuery;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchQuery;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Resolves audit visibility from current persisted staff and branch authority. */
@Component
class AuditQueryAuthorization {

    private static final String DENIED_MESSAGE =
            "Audit history is not available for the requested scope.";

    private final StaffScopeQuery staffScopeQuery;
    private final AuthorizedBranchQuery authorizedBranchQuery;
    private final GymBranchReportingQuery gymBranchReportingQuery;

    AuditQueryAuthorization(
            StaffScopeQuery staffScopeQuery,
            AuthorizedBranchQuery authorizedBranchQuery,
            GymBranchReportingQuery gymBranchReportingQuery) {
        this.staffScopeQuery = staffScopeQuery;
        this.authorizedBranchQuery = authorizedBranchQuery;
        this.gymBranchReportingQuery = gymBranchReportingQuery;
    }

    AuditVisibilityScope authorizeQuery(UUID actorUserId, Set<UUID> requestedBranchIds) {
        if (requestedBranchIds == null
                || requestedBranchIds.size() > AuditQueryPolicy.MAX_BRANCH_IDS) {
            throw denied();
        }
        StaffAuthorizationContext actor = currentActor(actorUserId);

        if (actor.organizationAdmin()) {
            if (requestedBranchIds.isEmpty()) {
                if (!AuditBranchVisibilityPolicy.allowsQuery(actor, true, Set.of())) {
                    throw denied();
                }
                return AuditVisibilityScope.organization();
            }
            if (!AuditBranchVisibilityPolicy.allowsQuery(
                    actor, false, requestedBranchIds)) {
                throw denied();
            }
            requireCanonicalOrganizationBranches(requestedBranchIds);
            return AuditVisibilityScope.branches(requestedBranchIds);
        }

        if (!actor.branchAdmin()
                || requestedBranchIds.isEmpty()) {
            throw denied();
        }
        Set<UUID> activeAssignedBranchIds = activeAssignedBranches(actor);
        StaffAuthorizationContext activeActor = withAssignments(
                actor, activeAssignedBranchIds);
        if (!AuditBranchVisibilityPolicy.allowsQuery(
                activeActor, false, requestedBranchIds)) {
            throw denied();
        }
        return AuditVisibilityScope.branches(requestedBranchIds);
    }

    AuditVisibilityScope authorizeDetail(UUID actorUserId) {
        StaffAuthorizationContext actor = currentActor(actorUserId);
        if (actor.organizationAdmin()) {
            return AuditVisibilityScope.organization();
        }
        if (!actor.branchAdmin()) {
            throw denied();
        }
        Set<UUID> activeAssignedBranchIds = activeAssignedBranches(actor);
        if (activeAssignedBranchIds.size() > AuditQueryPolicy.MAX_BRANCH_IDS) {
            throw denied();
        }
        StaffAuthorizationContext activeActor = withAssignments(
                actor, activeAssignedBranchIds);
        if (!AuditBranchVisibilityPolicy.allowsQuery(
                activeActor, false, activeAssignedBranchIds)) {
            throw denied();
        }
        return AuditVisibilityScope.branches(activeAssignedBranchIds);
    }

    private StaffAuthorizationContext currentActor(UUID actorUserId) {
        if (actorUserId == null) {
            throw denied();
        }
        try {
            return staffScopeQuery.findAuthorizationContext(actorUserId)
                    .filter(context -> context.userId().equals(actorUserId))
                    .orElseThrow(AuditQueryAuthorization::denied);
        } catch (AccessDeniedException exception) {
            throw exception;
        } catch (RuntimeException unavailable) {
            throw denied();
        }
    }

    private Set<UUID> activeAssignedBranches(StaffAuthorizationContext actor) {
        try {
            Set<UUID> authorizedActiveIds = authorizedBranchQuery
                    .findAuthorizedActiveBranches(actor.userId()).stream()
                    .map(AuthorizedBranchSummary::id)
                    .collect(Collectors.toUnmodifiableSet());
            return actor.assignedBranchIds().stream()
                    .filter(authorizedActiveIds::contains)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (RuntimeException unavailable) {
            throw denied();
        }
    }

    private void requireCanonicalOrganizationBranches(Set<UUID> branchIds) {
        try {
            Set<UUID> canonicalIds = gymBranchReportingQuery
                    .findCanonicalBranches(branchIds, true).stream()
                    .map(branch -> branch.id())
                    .collect(Collectors.toUnmodifiableSet());
            if (!canonicalIds.containsAll(branchIds)) {
                throw denied();
            }
        } catch (AccessDeniedException exception) {
            throw exception;
        } catch (RuntimeException unavailable) {
            throw denied();
        }
    }

    private static StaffAuthorizationContext withAssignments(
            StaffAuthorizationContext actor,
            Set<UUID> assignedBranchIds) {
        return new StaffAuthorizationContext(
                actor.userId(),
                actor.roles(),
                actor.accountStatus(),
                actor.scopeType(),
                assignedBranchIds);
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException(DENIED_MESSAGE);
    }
}
