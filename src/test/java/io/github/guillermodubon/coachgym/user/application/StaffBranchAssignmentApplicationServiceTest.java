package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.user.AssignStaffToBranchCommand;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchQuery;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.ChangeStaffScopeCommand;
import io.github.guillermodubon.coachgym.user.EndStaffBranchAssignmentCommand;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffBranchAssigned;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentDetails;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentEnded;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentQuery;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeAuthorizationPolicy;
import io.github.guillermodubon.coachgym.user.StaffScopeChanged;
import io.github.guillermodubon.coachgym.user.StaffScopeDetails;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import io.github.guillermodubon.coachgym.user.StaffScopeStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
class StaffBranchAssignmentApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID ACTOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID BRANCH_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_BRANCH_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "organization-admin");

    @Mock
    private StaffScopeQuery scopeQuery;
    @Mock
    private StaffBranchAssignmentQuery assignmentQuery;
    @Mock
    private AuthorizedBranchQuery authorizedBranchQuery;
    @Mock
    private StaffAssignmentAuthorizationQuery authorizationQuery;
    @Mock
    private StaffScopeStore scopeStore;
    @Mock
    private StaffBranchAssignmentStore assignmentStore;
    @Mock
    private StaffBranchAssignmentAdminQuery adminQuery;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private StaffBranchAssignmentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new StaffBranchAssignmentApplicationService(
                scopeQuery,
                assignmentQuery,
                authorizedBranchQuery,
                authorizationQuery,
                scopeStore,
                assignmentStore,
                adminQuery,
                eventPublisher,
                CLOCK);
    }

    @Test
    void organizationAdminAssignsReceptionistAndPublishesSafeEvent() {
        AssignStaffToBranchCommand command =
                new AssignStaffToBranchCommand(TARGET_ID, BRANCH_ID, "initial branch coverage");
        StaffBranchAssignmentDetails assigned = activeAssignment(0);
        when(scopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.of(organizationAdmin(ACTOR_ID)));
        when(scopeQuery.findAuthorizationContext(TARGET_ID))
                .thenReturn(Optional.of(branchReceptionist(TARGET_ID, BRANCH_ID)));
        when(authorizedBranchQuery.findAuthorizedActiveBranches(ACTOR_ID))
                .thenReturn(List.of(activeBranch(BRANCH_ID)));
        when(authorizationQuery.hasActiveBranchAssignment(TARGET_ID, BRANCH_ID))
                .thenReturn(false);
        when(assignmentStore.assign(command, ACTOR_ID, NOW)).thenReturn(assigned);

        assertThat(service.assign(command, ACTOR)).isEqualTo(assigned);

        ArgumentCaptor<StaffBranchAssigned> event =
                ArgumentCaptor.forClass(StaffBranchAssigned.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().assignmentId()).isEqualTo(ASSIGNMENT_ID);
        assertThat(event.getValue().targetUserId()).isEqualTo(TARGET_ID);
        assertThat(event.getValue().branchId()).isEqualTo(BRANCH_ID);
        assertThat(event.getValue().actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(event.getValue().reasonPresent()).isTrue();
        assertThat(event.getValue().toString())
                .doesNotContain("password", "photo", "session", "token");
    }

    @Test
    void branchAdminCannotManageAssignmentsAndFailurePublishesNothing() {
        AuthenticatedActor branchAdmin = new AuthenticatedActor(ACTOR_ID, "branch-admin");
        when(scopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.of(branchAdmin(ACTOR_ID, BRANCH_ID)));

        assertThatThrownBy(() -> service.assign(
                new AssignStaffToBranchCommand(TARGET_ID, BRANCH_ID, "denied"), branchAdmin))
                .isInstanceOf(io.github.guillermodubon.coachgym.user.StaffBranchAuthorizationException.class);

        verify(assignmentStore, never()).assign(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void branchAdminCannotReadOrganizationAssignmentOrScopeAdministration() {
        AuthenticatedActor branchAdmin = new AuthenticatedActor(ACTOR_ID, "branch-admin");
        when(scopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.of(branchAdmin(ACTOR_ID, BRANCH_ID)));

        assertThatThrownBy(() -> service.findAssignments(
                StaffBranchAssignmentSearchQuery.defaults(), branchAdmin))
                .isInstanceOf(io.github.guillermodubon.coachgym.user.StaffBranchAuthorizationException.class);
        assertThatThrownBy(() -> service.findAssignmentsForUser(
                TARGET_ID, StaffBranchAssignmentSearchQuery.defaults(), branchAdmin))
                .isInstanceOf(io.github.guillermodubon.coachgym.user.StaffBranchAuthorizationException.class);
        assertThatThrownBy(() -> service.findScope(TARGET_ID, branchAdmin))
                .isInstanceOf(io.github.guillermodubon.coachgym.user.StaffBranchAuthorizationException.class);

        verify(adminQuery, never()).findPage(any());
        verify(scopeQuery, never()).findScope(TARGET_ID);
    }

    @Test
    void selfAssignmentAndInactiveBranchAreRejectedBeforePersistence() {
        when(scopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.of(organizationAdmin(ACTOR_ID)));

        assertThatThrownBy(() -> service.assign(
                new AssignStaffToBranchCommand(ACTOR_ID, BRANCH_ID, "self"), ACTOR))
                .isInstanceOf(io.github.guillermodubon.coachgym.user.StaffScopeStateConflictException.class);

        when(scopeQuery.findAuthorizationContext(TARGET_ID))
                .thenReturn(Optional.of(branchReceptionist(TARGET_ID, BRANCH_ID)));
        when(authorizedBranchQuery.findAuthorizedActiveBranches(ACTOR_ID))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.assign(
                new AssignStaffToBranchCommand(TARGET_ID, BRANCH_ID, "inactive"), ACTOR))
                .isInstanceOf(StaffBranchAssignmentStateConflictException.class);

        verify(assignmentStore, never()).assign(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void duplicateAssignmentIsRejectedBeforeWriting() {
        when(scopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.of(organizationAdmin(ACTOR_ID)));
        when(scopeQuery.findAuthorizationContext(TARGET_ID))
                .thenReturn(Optional.of(branchReceptionist(TARGET_ID, BRANCH_ID)));
        when(authorizedBranchQuery.findAuthorizedActiveBranches(ACTOR_ID))
                .thenReturn(List.of(activeBranch(BRANCH_ID)));
        when(authorizationQuery.hasActiveBranchAssignment(TARGET_ID, BRANCH_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.assign(
                new AssignStaffToBranchCommand(TARGET_ID, BRANCH_ID, "duplicate"), ACTOR))
                .isInstanceOf(StaffBranchAssignmentStateConflictException.class);

        verify(assignmentStore, never()).assign(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void endingAssignmentRetainsAnotherActiveAssignmentAndPublishesEvent() {
        EndStaffBranchAssignmentCommand command =
                new EndStaffBranchAssignmentCommand(ASSIGNMENT_ID, "branch rotation", 0);
        StaffBranchAssignmentDetails current = activeAssignment(0);
        StaffBranchAssignmentDetails ended = endedAssignment(1);
        when(scopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.of(organizationAdmin(ACTOR_ID)));
        when(assignmentQuery.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(current));
        when(scopeQuery.findAuthorizationContext(TARGET_ID))
                .thenReturn(Optional.of(branchReceptionist(TARGET_ID, BRANCH_ID, OTHER_BRANCH_ID)));
        when(authorizationQuery.hasAnotherActiveBranchAssignment(TARGET_ID, ASSIGNMENT_ID))
                .thenReturn(true);
        when(assignmentStore.end(command, ACTOR_ID, NOW)).thenReturn(ended);

        assertThat(service.end(command, ACTOR)).isEqualTo(ended);

        ArgumentCaptor<StaffBranchAssignmentEnded> event =
                ArgumentCaptor.forClass(StaffBranchAssignmentEnded.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().assignmentId()).isEqualTo(ASSIGNMENT_ID);
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(event.getValue().reasonPresent()).isTrue();
    }

    @Test
    void endingLastAssignmentOfBranchScopedStaffIsRejected() {
        EndStaffBranchAssignmentCommand command =
                new EndStaffBranchAssignmentCommand(ASSIGNMENT_ID, "would lock out", 0);
        when(scopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.of(organizationAdmin(ACTOR_ID)));
        when(assignmentQuery.findById(ASSIGNMENT_ID))
                .thenReturn(Optional.of(activeAssignment(0)));
        when(scopeQuery.findAuthorizationContext(TARGET_ID))
                .thenReturn(Optional.of(branchReceptionist(TARGET_ID, BRANCH_ID)));
        when(authorizationQuery.hasAnotherActiveBranchAssignment(TARGET_ID, ASSIGNMENT_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> service.end(command, ACTOR))
                .isInstanceOf(StaffBranchAssignmentStateConflictException.class);

        verify(assignmentStore, never()).end(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void staleScopeVersionDoesNotWriteOrPublish() {
        StaffScopeDetails current = scope(TARGET_ID, StaffScopeType.BRANCH, 2);
        when(scopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.of(organizationAdmin(ACTOR_ID)));
        when(scopeQuery.findScope(TARGET_ID)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.changeScope(
                new ChangeStaffScopeCommand(TARGET_ID, StaffScopeType.ORGANIZATION, "stale", 1),
                ACTOR))
                .isInstanceOf(StaffScopeVersionConflictException.class);

        verify(scopeStore, never()).update(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void scopePromotionRetainsAssignmentsAndPublishesTransitionEvent() {
        StaffScopeDetails current = scope(TARGET_ID, StaffScopeType.BRANCH, 2);
        StaffScopeDetails updated = scope(TARGET_ID, StaffScopeType.ORGANIZATION, 3);
        ChangeStaffScopeCommand command = new ChangeStaffScopeCommand(
                TARGET_ID, StaffScopeType.ORGANIZATION, "organization duty", 2);
        when(scopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.of(organizationAdmin(ACTOR_ID)));
        when(scopeQuery.findScope(TARGET_ID)).thenReturn(Optional.of(current));
        when(scopeQuery.findAuthorizationContext(TARGET_ID))
                .thenReturn(Optional.of(branchAdmin(TARGET_ID, BRANCH_ID)));
        when(assignmentQuery.findActive(TARGET_ID))
                .thenReturn(List.of(activeAssignment(0)));
        when(authorizationQuery.countActiveOrganizationAdministrators()).thenReturn(2L);
        when(scopeStore.update(command, ACTOR_ID, NOW)).thenReturn(updated);

        assertThat(service.changeScope(command, ACTOR)).isEqualTo(updated);

        ArgumentCaptor<StaffScopeChanged> event = ArgumentCaptor.forClass(StaffScopeChanged.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().previousScope()).isEqualTo(StaffScopeType.BRANCH);
        assertThat(event.getValue().newScope()).isEqualTo(StaffScopeType.ORGANIZATION);
    }

    @Test
    void lastOrganizationAdministratorCannotBeDemoted() {
        StaffScopeDetails current = scope(TARGET_ID, StaffScopeType.ORGANIZATION, 0);
        ChangeStaffScopeCommand command = new ChangeStaffScopeCommand(
                TARGET_ID, StaffScopeType.BRANCH, "not safe", 0);
        when(scopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.of(organizationAdmin(ACTOR_ID)));
        when(scopeQuery.findScope(TARGET_ID)).thenReturn(Optional.of(current));
        when(scopeQuery.findAuthorizationContext(TARGET_ID))
                .thenReturn(Optional.of(organizationAdmin(TARGET_ID)));
        when(assignmentQuery.findActive(TARGET_ID)).thenReturn(List.of(activeAssignment(0)));
        when(authorizationQuery.countActiveOrganizationAdministrators()).thenReturn(1L);

        assertThatThrownBy(() -> service.changeScope(command, ACTOR))
                .isInstanceOf(StaffScopeStateConflictException.class);

        verify(scopeStore, never()).update(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void sameScopeIsNoOpAndDoesNotPublishEvent() {
        StaffScopeDetails current = scope(TARGET_ID, StaffScopeType.BRANCH, 4);
        when(scopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.of(organizationAdmin(ACTOR_ID)));
        when(scopeQuery.findScope(TARGET_ID)).thenReturn(Optional.of(current));
        when(scopeQuery.findAuthorizationContext(TARGET_ID))
                .thenReturn(Optional.of(branchReceptionist(TARGET_ID, BRANCH_ID)));

        assertThat(service.changeScope(
                new ChangeStaffScopeCommand(TARGET_ID, StaffScopeType.BRANCH, "no-op", 4), ACTOR))
                .isEqualTo(current);

        verify(scopeStore, never()).update(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private static StaffAuthorizationContext organizationAdmin(UUID userId) {
        return new StaffAuthorizationContext(
                userId, Set.of(RoleCode.ADMIN), StaffAccountStatus.ACTIVE,
                StaffScopeType.ORGANIZATION, Set.of());
    }

    private static StaffAuthorizationContext branchAdmin(UUID userId, UUID... branchIds) {
        return new StaffAuthorizationContext(
                userId, Set.of(RoleCode.ADMIN), StaffAccountStatus.ACTIVE,
                StaffScopeType.BRANCH, Set.of(branchIds));
    }

    private static StaffAuthorizationContext branchReceptionist(
            UUID userId, UUID... branchIds) {
        return new StaffAuthorizationContext(
                userId, Set.of(RoleCode.RECEPTIONIST), StaffAccountStatus.ACTIVE,
                StaffScopeType.BRANCH, Set.of(branchIds));
    }

    private static StaffScopeDetails scope(UUID userId, StaffScopeType type, long version) {
        Set<RoleCode> roles = type == StaffScopeType.ORGANIZATION
                ? Set.of(RoleCode.ADMIN) : Set.of(RoleCode.ADMIN);
        return new StaffScopeDetails(userId, roles, type, NOW.minusSeconds(60), null, version);
    }

    private static AuthorizedBranchSummary activeBranch(UUID branchId) {
        return new AuthorizedBranchSummary(
                branchId,
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                "BRANCH-" + branchId.toString().substring(0, 4),
                "Main Branch",
                "America/El_Salvador",
                true);
    }

    private static StaffBranchAssignmentDetails activeAssignment(long version) {
        return new StaffBranchAssignmentDetails(
                ASSIGNMENT_ID, TARGET_ID, BRANCH_ID, StaffBranchAssignmentStatus.ACTIVE,
                NOW.minusSeconds(600), ACTOR_ID, null, null, null, version);
    }

    private static StaffBranchAssignmentDetails endedAssignment(long version) {
        return new StaffBranchAssignmentDetails(
                ASSIGNMENT_ID, TARGET_ID, BRANCH_ID, StaffBranchAssignmentStatus.ENDED,
                NOW.minusSeconds(600), ACTOR_ID, NOW, ACTOR_ID, "branch rotation", version);
    }
}
