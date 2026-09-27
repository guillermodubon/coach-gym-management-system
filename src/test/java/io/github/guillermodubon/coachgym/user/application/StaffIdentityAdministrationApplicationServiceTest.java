package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.shared.security.CurrentPasswordVerifier;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.ChangeStaffIdentityStatusCommand;
import io.github.guillermodubon.coachgym.user.ChangeStaffRoleScopeCommand;
import io.github.guillermodubon.coachgym.user.LastOrganizationAdministratorPolicy;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffIdentityAssignmentsClosed;
import io.github.guillermodubon.coachgym.user.StaffIdentityAuthorizationException;
import io.github.guillermodubon.coachgym.user.StaffIdentityLifecycleChanged;
import io.github.guillermodubon.coachgym.user.StaffIdentityLifecyclePolicy;
import io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffIdentityStatus;
import io.github.guillermodubon.coachgym.user.StaffRoleScopeChanged;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class StaffIdentityAdministrationApplicationServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000009101");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000009102");
    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AuthenticatedActor ACTOR = new AuthenticatedActor(ACTOR_ID, "root-admin");

    @Mock private StaffScopeQuery scopeQuery;
    @Mock private StaffAssignmentAuthorizationQuery authorizationQuery;
    @Mock private StaffBranchAssignmentStore assignmentStore;
    @Mock private StaffIdentityAdministrationStore identityStore;
    @Mock private CurrentPasswordVerifier passwordVerifier;
    @Mock private ApplicationEventPublisher eventPublisher;

    private StaffIdentityAdministrationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new StaffIdentityAdministrationApplicationService(
                scopeQuery, authorizationQuery, assignmentStore, identityStore,
                passwordVerifier, eventPublisher, CLOCK);
        when(scopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.of(organizationAdmin()));
    }

    @Test
    void suspensionRetainsAssignmentsAndDoesNotRequireReauthenticationForReceptionist() {
        StaffIdentityAdministrationState active = state(
                StaffAccountStatus.ACTIVE, Set.of(RoleCode.RECEPTIONIST), StaffScopeType.BRANCH, 4, 2);
        StaffIdentityAdministrationState suspended = state(
                StaffAccountStatus.SUSPENDED, Set.of(RoleCode.RECEPTIONIST), StaffScopeType.BRANCH, 5, 2);
        ChangeStaffIdentityStatusCommand command = statusCommand(StaffIdentityStatus.SUSPENDED, 4);
        when(identityStore.find(TARGET_ID)).thenReturn(Optional.of(active));
        when(identityStore.changeStatus(
                TARGET_ID, StaffIdentityStatus.ACTIVE, StaffIdentityStatus.SUSPENDED,
                4, ACTOR_ID, NOW)).thenReturn(suspended);

        assertThat(service.changeStatus(command, ACTOR, null)).isEqualTo(suspended);

        verify(assignmentStore, never()).endAllActiveForUser(TARGET_ID, ACTOR_ID, command.reason(), NOW);
        verify(passwordVerifier, never()).verify(ACTOR_ID, ACTOR.username(), null);
        verify(eventPublisher).publishEvent(new StaffIdentityLifecycleChanged(
                TARGET_ID, StaffIdentityStatus.ACTIVE, StaffIdentityStatus.SUSPENDED,
                ACTOR_ID, NOW, true));
    }

    @Test
    void deactivationEndsAssignmentsAtomicallyAndPublishesOnlySafeFacts() {
        StaffIdentityAdministrationState active = state(
                StaffAccountStatus.ACTIVE, Set.of(RoleCode.RECEPTIONIST), StaffScopeType.BRANCH, 7, 3);
        StaffIdentityAdministrationState deactivated = state(
                StaffAccountStatus.DEACTIVATED, Set.of(RoleCode.RECEPTIONIST), StaffScopeType.BRANCH, 8, 3);
        ChangeStaffIdentityStatusCommand command = statusCommand(StaffIdentityStatus.DEACTIVATED, 7);
        when(identityStore.find(TARGET_ID)).thenReturn(Optional.of(active));
        when(assignmentStore.endAllActiveForUser(TARGET_ID, ACTOR_ID, command.reason(), NOW))
                .thenReturn(2);
        when(identityStore.changeStatus(
                TARGET_ID, StaffIdentityStatus.ACTIVE, StaffIdentityStatus.DEACTIVATED,
                7, ACTOR_ID, NOW)).thenReturn(deactivated);

        assertThat(service.changeStatus(command, ACTOR, null)).isEqualTo(deactivated);

        verify(eventPublisher).publishEvent(new StaffIdentityAssignmentsClosed(
                TARGET_ID, ACTOR_ID, 2, NOW, true));
        verify(eventPublisher).publishEvent(new StaffIdentityLifecycleChanged(
                TARGET_ID, StaffIdentityStatus.ACTIVE, StaffIdentityStatus.DEACTIVATED,
                ACTOR_ID, NOW, true));
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(events.capture());
        assertThat(events.getAllValues().toString())
                .doesNotContain(command.reason(), "current-password", "passwordHash", "email");
    }

    @Test
    void reactivationRequiresAnActiveBranchAssignment() {
        StaffIdentityAdministrationState suspended = state(
                StaffAccountStatus.SUSPENDED, Set.of(RoleCode.RECEPTIONIST), StaffScopeType.BRANCH, 9, 2);
        when(identityStore.find(TARGET_ID)).thenReturn(Optional.of(suspended));
        when(authorizationQuery.hasActiveBranchAssignment(TARGET_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.changeStatus(
                statusCommand(StaffIdentityStatus.ACTIVE, 9), ACTOR, null))
                .isInstanceOf(StaffIdentityStateConflictException.class);

        verify(identityStore, never()).changeStatus(
                TARGET_ID, StaffIdentityStatus.SUSPENDED, StaffIdentityStatus.ACTIVE,
                9, ACTOR_ID, NOW);
    }

    @Test
    void suspendingOrganizationAdministratorRequiresReauthenticationAndProtectsLastAdmin() {
        StaffIdentityAdministrationState active = state(
                StaffAccountStatus.ACTIVE, Set.of(RoleCode.ADMIN), StaffScopeType.ORGANIZATION, 1, 0);
        when(identityStore.find(TARGET_ID)).thenReturn(Optional.of(active));
        when(passwordVerifier.verify(ACTOR_ID, ACTOR.username(), "current-password")).thenReturn(true);
        when(authorizationQuery.countActiveOrganizationAdministrators()).thenReturn(1L);

        assertThatThrownBy(() -> service.changeStatus(
                statusCommand(StaffIdentityStatus.SUSPENDED, 1), ACTOR, "current-password"))
                .isInstanceOf(StaffIdentityStateConflictException.class);

        verify(passwordVerifier).verify(ACTOR_ID, ACTOR.username(), "current-password");
        verify(identityStore, never()).changeStatus(
                TARGET_ID, StaffIdentityStatus.ACTIVE, StaffIdentityStatus.SUSPENDED,
                1, ACTOR_ID, NOW);
    }

    @Test
    void failedReauthenticationDoesNotChangeRoleOrScope() {
        StaffIdentityAdministrationState active = state(
                StaffAccountStatus.ACTIVE, Set.of(RoleCode.RECEPTIONIST), StaffScopeType.BRANCH, 3, 5);
        ChangeStaffRoleScopeCommand command = roleCommand(Set.of(RoleCode.ADMIN), StaffScopeType.BRANCH, 3);
        when(identityStore.find(TARGET_ID)).thenReturn(Optional.of(active));
        when(passwordVerifier.verify(ACTOR_ID, ACTOR.username(), "wrong-password")).thenReturn(false);

        assertThatThrownBy(() -> service.changeRoleScope(command, ACTOR, "wrong-password"))
                .isInstanceOf(StaffIdentityAuthorizationException.class);

        verify(identityStore, never()).changeRoleScope(command, 5, ACTOR_ID, NOW);
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rolePromotionRequiresReauthenticationAndPublishesSafeTransition() {
        StaffIdentityAdministrationState active = state(
                StaffAccountStatus.ACTIVE, Set.of(RoleCode.RECEPTIONIST), StaffScopeType.BRANCH, 3, 5);
        StaffIdentityAdministrationState promoted = state(
                StaffAccountStatus.ACTIVE, Set.of(RoleCode.ADMIN), StaffScopeType.BRANCH, 4, 5);
        ChangeStaffRoleScopeCommand command = roleCommand(Set.of(RoleCode.ADMIN), StaffScopeType.BRANCH, 3);
        when(identityStore.find(TARGET_ID)).thenReturn(Optional.of(active));
        when(authorizationQuery.hasActiveBranchAssignment(TARGET_ID)).thenReturn(true);
        when(passwordVerifier.verify(ACTOR_ID, ACTOR.username(), "current-password")).thenReturn(true);
        when(identityStore.changeRoleScope(command, 5, ACTOR_ID, NOW)).thenReturn(promoted);

        assertThat(service.changeRoleScope(command, ACTOR, "current-password")).isEqualTo(promoted);

        verify(eventPublisher).publishEvent(new StaffRoleScopeChanged(
                TARGET_ID, Set.of(RoleCode.RECEPTIONIST), Set.of(RoleCode.ADMIN),
                StaffScopeType.BRANCH, StaffScopeType.BRANCH, ACTOR_ID, NOW, true));
    }

    @Test
    void staleVersionIsRejectedBeforeReauthenticationOrWrites() {
        StaffIdentityAdministrationState active = state(
                StaffAccountStatus.ACTIVE, Set.of(RoleCode.RECEPTIONIST), StaffScopeType.BRANCH, 3, 5);
        ChangeStaffRoleScopeCommand command = roleCommand(Set.of(RoleCode.ADMIN), StaffScopeType.BRANCH, 4);
        when(identityStore.find(TARGET_ID)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.changeRoleScope(command, ACTOR, "current-password"))
                .isInstanceOf(StaffIdentityVersionConflictException.class);

        verify(passwordVerifier, never()).verify(ACTOR_ID, ACTOR.username(), "current-password");
        verify(identityStore, never()).changeRoleScope(command, 5, ACTOR_ID, NOW);
    }

    @Test
    void selfLifecycleOperationIsRejected() {
        ChangeStaffIdentityStatusCommand command = new ChangeStaffIdentityStatusCommand(
                ACTOR_ID, StaffIdentityStatus.SUSPENDED, "self operation", 0);

        assertThatThrownBy(() -> service.changeStatus(command, ACTOR, null))
                .isInstanceOf(StaffIdentityAuthorizationException.class);

        verify(identityStore, never()).find(ACTOR_ID);
    }

    private static ChangeStaffIdentityStatusCommand statusCommand(
            StaffIdentityStatus status, long version) {
        return new ChangeStaffIdentityStatusCommand(TARGET_ID, status, "lifecycle reason", version);
    }

    private static ChangeStaffRoleScopeCommand roleCommand(
            Set<RoleCode> roles, StaffScopeType scope, long version) {
        return new ChangeStaffRoleScopeCommand(TARGET_ID, roles, scope, "authority reason", version);
    }

    private static StaffIdentityAdministrationState state(
            StaffAccountStatus status,
            Set<RoleCode> roles,
            StaffScopeType scope,
            long securityVersion,
            long scopeVersion) {
        return new StaffIdentityAdministrationState(
                TARGET_ID, status, roles, scope, securityVersion, scopeVersion);
    }

    private static io.github.guillermodubon.coachgym.user.StaffAuthorizationContext organizationAdmin() {
        return new io.github.guillermodubon.coachgym.user.StaffAuthorizationContext(
                ACTOR_ID, Set.of(RoleCode.ADMIN), StaffAccountStatus.ACTIVE,
                StaffScopeType.ORGANIZATION, Set.of());
    }
}
