package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffBranchAuthorizationPolicyTest {

    private static final UUID ORG_ADMIN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_ADMIN_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID RECEPTIONIST_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID TARGET_ID = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final UUID BRANCH_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_BRANCH_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void roleScopeMatrixAllowsOnlyApprovedCombinations() {
        assertThat(StaffScopeAuthorizationPolicy.isCompatible(
                Set.of(RoleCode.ADMIN), StaffScopeType.ORGANIZATION)).isTrue();
        assertThat(StaffScopeAuthorizationPolicy.isCompatible(
                Set.of(RoleCode.ADMIN), StaffScopeType.BRANCH)).isTrue();
        assertThat(StaffScopeAuthorizationPolicy.isCompatible(
                Set.of(RoleCode.RECEPTIONIST), StaffScopeType.BRANCH)).isTrue();
        assertThat(StaffScopeAuthorizationPolicy.isCompatible(
                Set.of(RoleCode.RECEPTIONIST), StaffScopeType.ORGANIZATION)).isFalse();
        assertThatThrownBy(() -> new StaffAuthorizationContext(
                RECEPTIONIST_ID, Set.of(RoleCode.RECEPTIONIST), StaffAccountStatus.ACTIVE,
                StaffScopeType.ORGANIZATION, Set.of()))
                .isInstanceOf(StaffScopeValidationException.class);
    }

    @Test
    void onlyActiveOrganizationAdminMayManageAndSelfChangesAreRejected() {
        StaffAuthorizationContext organizationAdmin = organizationAdmin();
        StaffAuthorizationContext branchAdmin = context(
                BRANCH_ADMIN_ID, Set.of(RoleCode.ADMIN), StaffScopeType.BRANCH, Set.of(BRANCH_ID));
        StaffAuthorizationContext receptionist = context(
                RECEPTIONIST_ID, Set.of(RoleCode.RECEPTIONIST), StaffScopeType.BRANCH, Set.of(BRANCH_ID));

        StaffScopeAuthorizationPolicy.requireOrganizationAdministrator(organizationAdmin);
        assertThatThrownBy(() -> StaffScopeAuthorizationPolicy.requireOrganizationAdministrator(branchAdmin))
                .isInstanceOf(StaffBranchAuthorizationException.class);
        assertThatThrownBy(() -> StaffScopeAuthorizationPolicy.requireOrganizationAdministrator(receptionist))
                .isInstanceOf(StaffBranchAuthorizationException.class);
        assertThatThrownBy(() -> StaffScopeAuthorizationPolicy.requireNotSelf(
                ORG_ADMIN_ID, ORG_ADMIN_ID))
                .isInstanceOf(StaffScopeStateConflictException.class);
    }

    @Test
    void branchAccessIsRestrictedToAssignmentsAndSelectionChecksActiveCatalog() {
        StaffAuthorizationContext organizationAdmin = organizationAdmin();
        StaffAuthorizationContext receptionist = context(
                RECEPTIONIST_ID, Set.of(RoleCode.RECEPTIONIST), StaffScopeType.BRANCH, Set.of(BRANCH_ID));

        assertThat(StaffBranchContextPolicy.canAccessBranch(organizationAdmin, OTHER_BRANCH_ID)).isTrue();
        assertThat(StaffBranchContextPolicy.canAccessBranch(receptionist, BRANCH_ID)).isTrue();
        assertThat(StaffBranchContextPolicy.canAccessBranch(receptionist, OTHER_BRANCH_ID)).isFalse();
        StaffBranchContextPolicy.requireSelectionAllowed(
                receptionist, new SelectActiveBranchCommand(BRANCH_ID), Set.of(BRANCH_ID));
        assertThatThrownBy(() -> StaffBranchContextPolicy.requireSelectionAllowed(
                receptionist, new SelectActiveBranchCommand(OTHER_BRANCH_ID), Set.of(OTHER_BRANCH_ID)))
                .isInstanceOf(StaffBranchAuthorizationException.class);
        assertThatThrownBy(() -> StaffBranchContextPolicy.requireSelectionAllowed(
                receptionist, new SelectActiveBranchCommand(BRANCH_ID), Set.of()))
                .isInstanceOf(StaffScopeStateConflictException.class);
        StaffBranchContext organizationContext = new StaffBranchContext(
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                StaffScopeType.ORGANIZATION, OTHER_BRANCH_ID,
                List.of(new AuthorizedBranchSummary(
                        OTHER_BRANCH_ID,
                        UUID.fromString("30000000-0000-0000-0000-000000000001"),
                        "OTHER", "Other branch", "America/El_Salvador", false)));
        // The context record itself is populated by a resolver with safe branch summaries;
        // this assertion documents that organization-wide admins may select a branch.
        assertThat(StaffBranchContextPolicy.canAccessBranch(organizationAdmin, OTHER_BRANCH_ID)).isTrue();
        assertThat(organizationContext.activeBranchId()).isEqualTo(OTHER_BRANCH_ID);
    }

    @Test
    void scopeChangesRequireAnAssignmentAndProtectTheLastOrganizationAdmin() {
        StaffAuthorizationContext organizationAdmin = organizationAdmin();
        StaffAuthorizationContext targetAdmin = context(
                TARGET_ID, Set.of(RoleCode.ADMIN), StaffScopeType.ORGANIZATION, Set.of());
        StaffAuthorizationContext targetReceptionist = context(
                TARGET_ID, Set.of(RoleCode.RECEPTIONIST), StaffScopeType.BRANCH, Set.of(BRANCH_ID));

        assertThatThrownBy(() -> StaffScopeAuthorizationPolicy.requireScopeChangeAllowed(
                organizationAdmin, targetReceptionist, StaffScopeType.BRANCH, false, 2))
                .isInstanceOf(StaffScopeStateConflictException.class);
        StaffScopeAuthorizationPolicy.requireScopeChangeAllowed(
                organizationAdmin, targetReceptionist, StaffScopeType.BRANCH, true, 2);
        assertThatThrownBy(() -> StaffScopeAuthorizationPolicy.requireScopeChangeAllowed(
                organizationAdmin, targetAdmin, StaffScopeType.BRANCH, true, 1))
                .isInstanceOf(StaffScopeStateConflictException.class);
    }

    private static StaffAuthorizationContext organizationAdmin() {
        return context(ORG_ADMIN_ID, Set.of(RoleCode.ADMIN), StaffScopeType.ORGANIZATION, Set.of());
    }

    private static StaffAuthorizationContext context(
            UUID userId, Set<RoleCode> roles, StaffScopeType scope, Set<UUID> branches) {
        return new StaffAuthorizationContext(
                userId, roles, StaffAccountStatus.ACTIVE, scope, branches);
    }
}
