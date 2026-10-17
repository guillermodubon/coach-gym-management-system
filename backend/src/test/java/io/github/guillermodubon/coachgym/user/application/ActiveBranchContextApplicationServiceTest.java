package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.user.ActiveBranchContextUnavailableException;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchQuery;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.SelectActiveBranchCommand;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffBranchContext;
import io.github.guillermodubon.coachgym.user.StaffScopeStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.github.guillermodubon.coachgym.user.RoleCode;
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
class ActiveBranchContextApplicationServiceTest {

    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ORGANIZATION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_A = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID BRANCH_B = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Mock
    private StaffScopeQuery scopeQuery;
    @Mock
    private AuthorizedBranchQuery authorizedBranchQuery;

    private InMemoryActiveBranchSessionStore sessionStore;
    private ActiveBranchContextApplicationService service;

    @BeforeEach
    void setUp() {
        sessionStore = new InMemoryActiveBranchSessionStore();
        service = new ActiveBranchContextApplicationService(scopeQuery, authorizedBranchQuery, sessionStore);
    }

    @Test
    void organizationAdminSeesDeterministicallySortedActiveBranches() {
        when(scopeQuery.findAuthorizationContext(USER_ID))
                .thenReturn(Optional.of(organizationAdmin()));
        when(authorizedBranchQuery.findAuthorizedOrganizationId(USER_ID))
                .thenReturn(Optional.of(ORGANIZATION_ID));
        when(authorizedBranchQuery.findAuthorizedActiveBranches(USER_ID))
                .thenReturn(List.of(branch("ZETA", BRANCH_A), branch("ALPHA", BRANCH_B)));
        sessionStore.select(BRANCH_B);

        StaffBranchContext context = service.resolve(USER_ID);

        assertThat(context.scopeType()).isEqualTo(StaffScopeType.ORGANIZATION);
        assertThat(context.activeBranchId()).isEqualTo(BRANCH_B);
        assertThat(context.availableBranches()).extracting(AuthorizedBranchSummary::code)
                .containsExactly("ALPHA", "ZETA");
    }

    @Test
    void branchScopedUserCanSelectOnlyAnActiveAssignedBranch() {
        when(scopeQuery.findAuthorizationContext(USER_ID))
                .thenReturn(Optional.of(branchReceptionist(BRANCH_B)));
        when(authorizedBranchQuery.findAuthorizedOrganizationId(USER_ID))
                .thenReturn(Optional.of(ORGANIZATION_ID));
        when(authorizedBranchQuery.findAuthorizedActiveBranches(USER_ID))
                .thenReturn(List.of(branch("ALPHA", BRANCH_B)));

        StaffBranchContext selected = service.select(
                USER_ID, new SelectActiveBranchCommand(BRANCH_B));

        assertThat(selected.activeBranchId()).isEqualTo(BRANCH_B);
        assertThat(sessionStore.selectedBranchId()).contains(BRANCH_B);

        assertThatThrownBy(() -> service.select(
                USER_ID, new SelectActiveBranchCommand(BRANCH_A)))
                .isInstanceOf(StaffScopeStateConflictException.class);
    }

    @Test
    void staleSelectionIsClearedAndCannotBeUsedAfterAssignmentEnds() {
        when(scopeQuery.findAuthorizationContext(USER_ID))
                .thenReturn(Optional.of(branchReceptionist(BRANCH_B)));
        when(authorizedBranchQuery.findAuthorizedOrganizationId(USER_ID))
                .thenReturn(Optional.of(ORGANIZATION_ID));
        when(authorizedBranchQuery.findAuthorizedActiveBranches(USER_ID))
                .thenReturn(List.of());
        sessionStore.select(BRANCH_B);

        assertThatThrownBy(() -> service.resolve(USER_ID))
                .isInstanceOf(ActiveBranchContextUnavailableException.class);
        assertThat(sessionStore.selectedBranchId()).isEmpty();
    }

    @Test
    void clearingContextDoesNotChangeAuthorizationFacts() {
        when(scopeQuery.findAuthorizationContext(USER_ID))
                .thenReturn(Optional.of(organizationAdmin()));
        sessionStore.select(BRANCH_A);

        service.clear(USER_ID);

        assertThat(sessionStore.selectedBranchId()).isEmpty();
        verify(scopeQuery).findAuthorizationContext(USER_ID);
    }

    @Test
    void missingScopeFailsClosedAndDoesNotExposeBranchData() {
        when(scopeQuery.findAuthorizationContext(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(USER_ID))
                .isInstanceOf(ActiveBranchContextUnavailableException.class);
        verify(authorizedBranchQuery, never()).findAuthorizedActiveBranches(USER_ID);
    }

    private StaffAuthorizationContext organizationAdmin() {
        return new StaffAuthorizationContext(
                USER_ID, Set.of(RoleCode.ADMIN), StaffAccountStatus.ACTIVE,
                StaffScopeType.ORGANIZATION, Set.of());
    }

    private StaffAuthorizationContext branchReceptionist(UUID branchId) {
        return new StaffAuthorizationContext(
                USER_ID, Set.of(RoleCode.RECEPTIONIST), StaffAccountStatus.ACTIVE,
                StaffScopeType.BRANCH, Set.of(branchId));
    }

    private AuthorizedBranchSummary branch(String code, UUID id) {
        return new AuthorizedBranchSummary(
                id, ORGANIZATION_ID, code, code + " branch", "America/El_Salvador", false);
    }

    private static final class InMemoryActiveBranchSessionStore
            implements ActiveBranchSessionStore {

        private UUID selected;

        @Override
        public Optional<UUID> selectedBranchId() {
            return Optional.ofNullable(selected);
        }

        @Override
        public void select(UUID branchId) {
            selected = branchId;
        }

        @Override
        public void clear() {
            selected = null;
        }
    }
}
