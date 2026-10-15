package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.shared.security.CurrentPasswordVerifier;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchQuery;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.CreateStaffInvitationCommand;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffInvitationPolicy;
import io.github.guillermodubon.coachgym.user.StaffInvitationStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
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

class StaffInvitationTransactionServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("81000000-0000-0000-0000-000000000001");
    private static final UUID ORGANIZATION_ID = UUID.fromString("81000000-0000-0000-0000-000000000002");
    private static final UUID BRANCH_ID = UUID.fromString("81000000-0000-0000-0000-000000000003");
    private static final String TOKEN = "A".repeat(43);
    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    private StaffInvitationPersistence invitations;
    private StaffScopeQuery scopeQuery;
    private AuthorizedBranchQuery branches;
    private StaffOneTimeTokenGenerator tokens;
    private StaffTokenProtector protector;
    private CurrentPasswordVerifier passwordVerifier;
    private StaffInvitationTransactionService service;

    @BeforeEach
    void setUp() {
        invitations = org.mockito.Mockito.mock(StaffInvitationPersistence.class);
        scopeQuery = org.mockito.Mockito.mock(StaffScopeQuery.class);
        branches = org.mockito.Mockito.mock(AuthorizedBranchQuery.class);
        tokens = org.mockito.Mockito.mock(StaffOneTimeTokenGenerator.class);
        protector = org.mockito.Mockito.mock(StaffTokenProtector.class);
        passwordVerifier = org.mockito.Mockito.mock(CurrentPasswordVerifier.class);
        service = new StaffInvitationTransactionService(
                invitations, scopeQuery, branches, tokens, protector, passwordVerifier,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(scopeQuery.findAuthorizationContext(ACTOR_ID)).thenReturn(Optional.of(
                new StaffAuthorizationContext(
                        ACTOR_ID, Set.of(RoleCode.ADMIN), StaffAccountStatus.ACTIVE,
                        StaffScopeType.ORGANIZATION, Set.of())));
        when(branches.findAuthorizedOrganizationId(ACTOR_ID)).thenReturn(Optional.of(ORGANIZATION_ID));
        when(branches.findAuthorizedActiveBranches(ACTOR_ID)).thenReturn(List.of(
                new AuthorizedBranchSummary(
                        BRANCH_ID, ORGANIZATION_ID, "NORTH", "North Branch",
                        "America/El_Salvador", false)));
        when(tokens.generate()).thenReturn(TOKEN);
        when(protector.fingerprint(TOKEN, StaffTokenPurpose.INVITATION))
                .thenReturn(new StaffTokenFingerprint(
                        "a".repeat(64), StaffTokenPurpose.INVITATION.schemeVersion()));
        when(invitations.create(any(StaffInvitationDraft.class))).thenAnswer(invocation ->
                recordFrom(invocation.getArgument(0)));
    }

    @Test
    void receptionistInvitationUsesOnlyActiveBranchesAndSkipsReauthentication() {
        CreateStaffInvitationCommand command = new CreateStaffInvitationCommand(
                " Staff@Example.Test ", RoleCode.RECEPTIONIST, StaffScopeType.BRANCH, Set.of(BRANCH_ID));

        PreparedStaffInvitationDelivery prepared = service.create(
                command, new AuthenticatedActor(ACTOR_ID, "admin-user"), null);

        assertThat(prepared.invitation().status()).isEqualTo(StaffInvitationStatus.PENDING);
        assertThat(prepared.email().recipient()).isEqualTo("staff@example.test");
        assertThat(prepared.email().branchNames()).containsExactly("North Branch");
        assertThat(prepared.email().token()).isEqualTo(TOKEN);
        assertThat(prepared.toString()).doesNotContain(TOKEN, "staff@example.test");
        verifyNoInteractions(passwordVerifier);
        verify(invitations).reserveDeliveryAttempt(prepared.invitation().invitationId(), 0, NOW);
    }

    @Test
    void administratorInvitationRequiresSuccessfulCurrentPasswordVerification() {
        when(passwordVerifier.verify(ACTOR_ID, "admin-user", "recent-password")).thenReturn(true);
        CreateStaffInvitationCommand command = new CreateStaffInvitationCommand(
                "owner@example.test", RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of());

        PreparedStaffInvitationDelivery prepared = service.create(
                command, new AuthenticatedActor(ACTOR_ID, "admin-user"), "recent-password");

        assertThat(prepared.invitation().proposedRole()).isEqualTo(RoleCode.ADMIN);
        verify(passwordVerifier).verify(ACTOR_ID, "admin-user", "recent-password");
    }

    @Test
    void failedReauthenticationAndInactiveBranchStopBeforeAnyInvitationWrite() {
        when(passwordVerifier.verify(ACTOR_ID, "admin-user", "bad-password")).thenReturn(false);
        CreateStaffInvitationCommand adminInvitation = new CreateStaffInvitationCommand(
                "owner@example.test", RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of());

        assertThatThrownBy(() -> service.create(
                adminInvitation, new AuthenticatedActor(ACTOR_ID, "admin-user"), "bad-password"))
                .isInstanceOf(StaffCurrentPasswordInvalidException.class)
                .hasMessageNotContaining("bad-password");
        verify(invitations, never()).create(any());

        CreateStaffInvitationCommand receptionistInvitation = new CreateStaffInvitationCommand(
                "reception@example.test", RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                Set.of(UUID.fromString("81000000-0000-0000-0000-000000000004")));
        assertThatThrownBy(() -> service.create(
                receptionistInvitation, new AuthenticatedActor(ACTOR_ID, "admin-user"), null))
                .isInstanceOf(StaffIdentityStateConflictException.class);
        verify(passwordVerifier, times(1)).verify(ACTOR_ID, "admin-user", "bad-password");
    }

    @Test
    void receptionistAndBranchAdministratorCannotManageInvitations() {
        UUID otherActor = UUID.fromString("81000000-0000-0000-0000-000000000005");
        when(scopeQuery.findAuthorizationContext(otherActor)).thenReturn(Optional.of(
                new StaffAuthorizationContext(
                        otherActor, Set.of(RoleCode.RECEPTIONIST), StaffAccountStatus.ACTIVE,
                        StaffScopeType.BRANCH, Set.of(BRANCH_ID))));
        CreateStaffInvitationCommand command = new CreateStaffInvitationCommand(
                "staff@example.test", RoleCode.RECEPTIONIST, StaffScopeType.BRANCH, Set.of(BRANCH_ID));

        assertThatThrownBy(() -> service.create(
                command, new AuthenticatedActor(otherActor, "receptionist"), null))
                .isInstanceOf(io.github.guillermodubon.coachgym.user.StaffBranchAuthorizationException.class);
        verify(invitations, never()).create(any());
    }

    private static StaffInvitationRecord recordFrom(StaffInvitationDraft draft) {
        return new StaffInvitationRecord(
                draft.invitationId(), draft.organizationId(), draft.invitedEmail(),
                draft.proposedRole(), draft.proposedScope(), draft.proposedBranchIds(),
                StaffInvitationStatus.PENDING, draft.tokenFingerprint(), draft.createdAt(),
                draft.lastSentAt(), draft.expiresAt(), null, null, null, null,
                draft.invitedByUserId(), 0);
    }
}
