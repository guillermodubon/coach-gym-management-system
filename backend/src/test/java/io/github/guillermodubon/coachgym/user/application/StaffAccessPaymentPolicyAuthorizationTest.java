package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyActor;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyAuthorizationException;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchQuery;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaffAccessPaymentPolicyAuthorizationTest {

    private static final UUID USER_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000001");
    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "60000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_ID = UUID.fromString(
            "70000000-0000-0000-0000-000000000001");
    private static final AccessPaymentPolicyActor ACTOR =
            new AccessPaymentPolicyActor(USER_ID, "staff-user");

    @Mock private StaffScopeQuery staffScopeQuery;
    @Mock private AuthorizedBranchQuery authorizedBranchQuery;

    private StaffAccessPaymentPolicyAuthorization authorization;

    @BeforeEach
    void setUp() {
        authorization = new StaffAccessPaymentPolicyAuthorization(
                staffScopeQuery, authorizedBranchQuery);
    }

    @Test
    void organizationAdministratorMayReadAuthorizedBranchAndManageOrganizationPolicy() {
        when(staffScopeQuery.findAuthorizationContext(USER_ID))
                .thenReturn(Optional.of(staff(Set.of(RoleCode.ADMIN), StaffScopeType.ORGANIZATION)));
        givenAuthorizedBranch();

        assertThat(authorization.requireOrganizationAdministrator(ACTOR))
                .isEqualTo(ORGANIZATION_ID);
        assertThat(authorization.requireAdministratorCanReadBranch(ACTOR, BRANCH_ID))
                .isEqualTo(ORGANIZATION_ID);
    }

    @Test
    void branchAdministratorMayReadOnlyOwnBranchAndCannotManageOrganizationPolicy() {
        when(staffScopeQuery.findAuthorizationContext(USER_ID))
                .thenReturn(Optional.of(staff(Set.of(RoleCode.ADMIN), StaffScopeType.BRANCH)));
        givenAuthorizedBranch();

        assertThat(authorization.requireAdministratorCanReadBranch(ACTOR, BRANCH_ID))
                .isEqualTo(ORGANIZATION_ID);
        assertThatThrownBy(() -> authorization.requireOrganizationAdministrator(ACTOR))
                .isInstanceOf(AccessPaymentPolicyAuthorizationException.class);
    }

    @Test
    void branchAdministratorMayReadOperationalPolicyOnlyAtAssignedBranch() {
        UUID otherBranch = UUID.fromString("70000000-0000-0000-0000-000000000002");
        when(staffScopeQuery.findAuthorizationContext(USER_ID))
                .thenReturn(Optional.of(staff(Set.of(RoleCode.ADMIN), StaffScopeType.BRANCH)));
        givenAuthorizedBranch();

        assertThat(authorization.requireOperationalStaffCanReadBranch(ACTOR, BRANCH_ID))
                .isEqualTo(ORGANIZATION_ID);
        assertThatThrownBy(() -> authorization
                .requireOperationalStaffCanReadBranch(ACTOR, otherBranch))
                .isInstanceOf(AccessPaymentPolicyAuthorizationException.class);
    }

    @Test
    void receptionistCannotReadAdministrativePolicyOrManageIt() {
        when(staffScopeQuery.findAuthorizationContext(USER_ID))
                .thenReturn(Optional.of(staff(
                        Set.of(RoleCode.RECEPTIONIST), StaffScopeType.BRANCH)));
        givenAuthorizedBranch();

        assertThatThrownBy(() -> authorization
                .requireAdministratorCanReadBranch(ACTOR, BRANCH_ID))
                .isInstanceOf(AccessPaymentPolicyAuthorizationException.class);
        assertThat(authorization.requireOperationalStaffCanReadBranch(ACTOR, BRANCH_ID))
                .isEqualTo(ORGANIZATION_ID);
        assertThatThrownBy(() -> authorization.requireOperationalStaffCanReadBranch(
                ACTOR, UUID.randomUUID()))
                .isInstanceOf(AccessPaymentPolicyAuthorizationException.class);
        assertThatThrownBy(() -> authorization.requireOrganizationAdministrator(ACTOR))
                .isInstanceOf(AccessPaymentPolicyAuthorizationException.class);
    }

    @Test
    void branchAdministratorCannotReadAnUnassignedBranch() {
        UUID otherBranch = UUID.fromString("70000000-0000-0000-0000-000000000002");
        when(staffScopeQuery.findAuthorizationContext(USER_ID))
                .thenReturn(Optional.of(staff(Set.of(RoleCode.ADMIN), StaffScopeType.BRANCH)));
        givenAuthorizedBranch();

        assertThatThrownBy(() -> authorization
                .requireAdministratorCanReadBranch(ACTOR, otherBranch))
                .isInstanceOf(AccessPaymentPolicyAuthorizationException.class);
    }

    @Test
    void inactiveStaffCannotManagePolicy() {
        StaffAuthorizationContext inactive = new StaffAuthorizationContext(
                USER_ID,
                Set.of(RoleCode.ADMIN),
                StaffAccountStatus.INACTIVE,
                StaffScopeType.ORGANIZATION,
                Set.of());
        when(staffScopeQuery.findAuthorizationContext(USER_ID))
                .thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> authorization.requireOrganizationAdministrator(ACTOR))
                .isInstanceOf(AccessPaymentPolicyAuthorizationException.class);
    }

    @Test
    void mismatchedStaffContextCannotManagePolicy() {
        UUID differentUserId = UUID.fromString("50000000-0000-0000-0000-000000000002");
        StaffAuthorizationContext mismatched = new StaffAuthorizationContext(
                differentUserId,
                Set.of(RoleCode.ADMIN),
                StaffAccountStatus.ACTIVE,
                StaffScopeType.ORGANIZATION,
                Set.of());
        when(staffScopeQuery.findAuthorizationContext(USER_ID))
                .thenReturn(Optional.of(mismatched));

        assertThatThrownBy(() -> authorization.requireOrganizationAdministrator(ACTOR))
                .isInstanceOf(AccessPaymentPolicyAuthorizationException.class);
    }

    private static StaffAuthorizationContext staff(
            Set<RoleCode> roles,
            StaffScopeType scope) {
        return new StaffAuthorizationContext(
                USER_ID, roles, StaffAccountStatus.ACTIVE, scope,
                scope == StaffScopeType.BRANCH ? Set.of(BRANCH_ID) : Set.of());
    }

    private void givenAuthorizedBranch() {
        when(authorizedBranchQuery.findAuthorizedOrganizationId(USER_ID))
                .thenReturn(Optional.of(ORGANIZATION_ID));
        when(authorizedBranchQuery.findAuthorizedActiveBranches(USER_ID))
                .thenReturn(List.of(new AuthorizedBranchSummary(
                        BRANCH_ID,
                        ORGANIZATION_ID,
                        "MAIN",
                        "Main Branch",
                        "America/El_Salvador",
                        true)));
    }
}
