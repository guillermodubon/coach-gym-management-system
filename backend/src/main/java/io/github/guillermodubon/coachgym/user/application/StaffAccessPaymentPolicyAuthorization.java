package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyActor;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyAuthorization;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyAuthorizationException;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchQuery;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffBranchAuthorizationException;
import io.github.guillermodubon.coachgym.user.StaffScopeAuthorizationPolicy;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolves policy authority from persisted roles, scope, assignments, and active branches. */
@Component
public class StaffAccessPaymentPolicyAuthorization implements AccessPaymentPolicyAuthorization {

    private static final String ORGANIZATION_ADMIN_REQUIRED =
            "An active organization administrator is required for this policy operation.";

    private final StaffScopeQuery staffScopeQuery;
    private final AuthorizedBranchQuery authorizedBranchQuery;

    public StaffAccessPaymentPolicyAuthorization(
            StaffScopeQuery staffScopeQuery,
            AuthorizedBranchQuery authorizedBranchQuery) {
        this.staffScopeQuery = Objects.requireNonNull(staffScopeQuery);
        this.authorizedBranchQuery = Objects.requireNonNull(authorizedBranchQuery);
    }

    @Override
    public UUID requireOrganizationAdministrator(AccessPaymentPolicyActor actor) {
        requireActor(actor);
        StaffAuthorizationContext staff = staffScopeQuery
                .findAuthorizationContext(actor.userId())
                .filter(context -> context.userId().equals(actor.userId()))
                .orElseThrow(StaffAccessPaymentPolicyAuthorization::denied);
        try {
            StaffScopeAuthorizationPolicy.requireOrganizationAdministrator(staff);
        } catch (StaffBranchAuthorizationException exception) {
            throw denied();
        }
        return authorizedBranchQuery.findAuthorizedOrganizationId(actor.userId())
                .orElseThrow(StaffAccessPaymentPolicyAuthorization::denied);
    }

    @Override
    public UUID requireAdministratorCanReadBranch(
            AccessPaymentPolicyActor actor,
            UUID branchId) {
        requireActor(actor);
        if (branchId == null) {
            throw denied();
        }
        StaffAuthorizationContext staff = staffScopeQuery
                .findAuthorizationContext(actor.userId())
                .filter(context -> context.userId().equals(actor.userId()))
                .orElseThrow(StaffAccessPaymentPolicyAuthorization::denied);
        UUID organizationId = authorizedBranchQuery
                .findAuthorizedOrganizationId(actor.userId())
                .orElseThrow(StaffAccessPaymentPolicyAuthorization::denied);
        boolean branchIsAuthorized = authorizedBranchQuery
                .findAuthorizedActiveBranches(actor.userId()).stream()
                .map(AuthorizedBranchSummary::id)
                .anyMatch(branchId::equals);

        boolean organizationAdmin = staff.organizationAdmin() && branchIsAuthorized;
        boolean branchAdmin = staff.branchAdmin()
                && staff.scopeType() == StaffScopeType.BRANCH
                && staff.assignedTo(branchId)
                && branchIsAuthorized;
        if (!organizationAdmin && !branchAdmin) {
            throw denied();
        }
        return organizationId;
    }

    @Override
    public UUID requireOperationalStaffCanReadBranch(
            AccessPaymentPolicyActor actor,
            UUID branchId) {
        requireActor(actor);
        if (branchId == null) {
            throw denied();
        }
        StaffAuthorizationContext staff = staffScopeQuery
                .findAuthorizationContext(actor.userId())
                .filter(context -> context.userId().equals(actor.userId()))
                .orElseThrow(StaffAccessPaymentPolicyAuthorization::denied);
        UUID organizationId = authorizedBranchQuery
                .findAuthorizedOrganizationId(actor.userId())
                .orElseThrow(StaffAccessPaymentPolicyAuthorization::denied);
        boolean branchIsAuthorized = authorizedBranchQuery
                .findAuthorizedActiveBranches(actor.userId()).stream()
                .map(AuthorizedBranchSummary::id)
                .anyMatch(branchId::equals);

        boolean organizationAdmin = staff.organizationAdmin() && branchIsAuthorized;
        boolean assignedBranchAdministrator = staff.branchAdmin()
                && staff.assignedTo(branchId)
                && branchIsAuthorized;
        boolean assignedReceptionist = staff.receptionist()
                && staff.assignedTo(branchId)
                && branchIsAuthorized;
        if (!organizationAdmin && !assignedBranchAdministrator && !assignedReceptionist) {
            throw denied();
        }
        return organizationId;
    }

    private static void requireActor(AccessPaymentPolicyActor actor) {
        if (actor == null || actor.userId() == null
                || actor.identifier() == null || actor.identifier().isBlank()) {
            throw denied();
        }
    }

    private static AccessPaymentPolicyAuthorizationException denied() {
        return new AccessPaymentPolicyAuthorizationException();
    }
}
