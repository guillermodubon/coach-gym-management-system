package io.github.guillermodubon.coachgym.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.audit.AuditVisibilityScope;
import io.github.guillermodubon.coachgym.organization.GymBranchReportingQuery;
import io.github.guillermodubon.coachgym.organization.GymBranchStatus;
import io.github.guillermodubon.coachgym.organization.GymBranchSummary;
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
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class AuditQueryAuthorizationTest {

    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID BRANCH_A = UUID.randomUUID();
    private static final UUID BRANCH_B = UUID.randomUUID();
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();

    @Mock private StaffScopeQuery staffScopeQuery;
    @Mock private AuthorizedBranchQuery authorizedBranchQuery;
    @Mock private GymBranchReportingQuery gymBranchReportingQuery;

    private AuditQueryAuthorization authorization;

    @BeforeEach
    void setUp() {
        authorization = new AuditQueryAuthorization(
                staffScopeQuery, authorizedBranchQuery, gymBranchReportingQuery);
    }

    @Test
    void organizationAdministratorCanQueryOrganizationOrCanonicalBranchSet() {
        when(staffScopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.of(actor(RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                        Set.of(BRANCH_A))));
        when(gymBranchReportingQuery.findCanonicalBranches(Set.of(BRANCH_A), true))
                .thenReturn(List.of(gymBranch(BRANCH_A)));

        assertThat(authorization.authorizeQuery(ACTOR_ID, Set.of()))
                .isEqualTo(AuditVisibilityScope.organization());
        assertThat(authorization.authorizeQuery(ACTOR_ID, Set.of(BRANCH_A)))
                .isEqualTo(AuditVisibilityScope.branches(Set.of(BRANCH_A)));
    }

    @Test
    void branchAdministratorCanQueryOnlyExplicitActiveAssignments() {
        when(staffScopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.of(actor(RoleCode.ADMIN, StaffScopeType.BRANCH,
                        Set.of(BRANCH_A, BRANCH_B))));
        when(authorizedBranchQuery.findAuthorizedActiveBranches(ACTOR_ID))
                .thenReturn(List.of(authorizedBranch(BRANCH_A)));

        assertThat(authorization.authorizeQuery(ACTOR_ID, Set.of(BRANCH_A)))
                .isEqualTo(AuditVisibilityScope.branches(Set.of(BRANCH_A)));
        assertThatThrownBy(() -> authorization.authorizeQuery(ACTOR_ID, Set.of(BRANCH_B)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Audit history is not available for the requested scope.");
        assertThatThrownBy(() -> authorization.authorizeQuery(ACTOR_ID, Set.of()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void branchDetailUsesCurrentActiveAssignmentAndNotCurrentSelection() {
        when(staffScopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.of(actor(RoleCode.ADMIN, StaffScopeType.BRANCH,
                        Set.of(BRANCH_A, BRANCH_B))));
        when(authorizedBranchQuery.findAuthorizedActiveBranches(ACTOR_ID))
                .thenReturn(List.of(authorizedBranch(BRANCH_A)));

        assertThat(authorization.authorizeDetail(ACTOR_ID))
                .isEqualTo(AuditVisibilityScope.branches(Set.of(BRANCH_A)));
        verify(authorizedBranchQuery).findAuthorizedActiveBranches(ACTOR_ID);
    }

    @Test
    void receptionistAndMissingPersistedScopeAreDeniedForQueriesAndDetails() {
        when(staffScopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.of(actor(RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                        Set.of(BRANCH_A))));
        assertThatThrownBy(() -> authorization.authorizeQuery(ACTOR_ID, Set.of(BRANCH_A)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorization.authorizeDetail(ACTOR_ID))
                .isInstanceOf(AccessDeniedException.class);
        when(staffScopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> authorization.authorizeQuery(ACTOR_ID, Set.of()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(authorizedBranchQuery, gymBranchReportingQuery);
    }

    @Test
    void persistedOrganizationScopeAuthorizesOrganizationWideExportQuery() {
        when(staffScopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.of(actor(RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                        Set.of(BRANCH_A))));

        assertThat(authorization.authorizeQuery(ACTOR_ID, Set.of()))
                .isEqualTo(AuditVisibilityScope.organization());

        verify(staffScopeQuery).findAuthorizationContext(ACTOR_ID);
        verifyNoInteractions(authorizedBranchQuery, gymBranchReportingQuery);
    }

    private static StaffAuthorizationContext actor(
            RoleCode role,
            StaffScopeType scope,
            Set<UUID> assignments) {
        return new StaffAuthorizationContext(
                ACTOR_ID, Set.of(role), StaffAccountStatus.ACTIVE, scope, assignments);
    }

    private static AuthorizedBranchSummary authorizedBranch(UUID branchId) {
        return new AuthorizedBranchSummary(
                branchId, ORGANIZATION_ID, "AUD-" + branchId.toString().substring(0, 6),
                "Audit test branch", "America/El_Salvador", true);
    }

    private static GymBranchSummary gymBranch(UUID branchId) {
        return new GymBranchSummary(
                branchId, ORGANIZATION_ID, "AUD-" + branchId.toString().substring(0, 6),
                "Audit test branch", "America/El_Salvador", GymBranchStatus.ACTIVE, true);
    }
}
