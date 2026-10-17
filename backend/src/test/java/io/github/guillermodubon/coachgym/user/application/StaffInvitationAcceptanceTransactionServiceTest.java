package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.user.AcceptStaffInvitationCommand;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchQuery;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffInvitationPolicy;
import io.github.guillermodubon.coachgym.user.StaffInvitationStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.github.guillermodubon.coachgym.user.StaffTokenPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;

class StaffInvitationAcceptanceTransactionServiceTest {

    private static final UUID INVITATION_ID = UUID.fromString("83000000-0000-0000-0000-000000000001");
    private static final UUID ORGANIZATION_ID = UUID.fromString("83000000-0000-0000-0000-000000000002");
    private static final UUID INVITER_ID = UUID.fromString("83000000-0000-0000-0000-000000000003");
    private static final UUID BRANCH_ID = UUID.fromString("83000000-0000-0000-0000-000000000004");
    private static final String TOKEN = "A".repeat(StaffTokenPolicy.TOKEN_LENGTH);
    private static final String PASSWORD = "a-long-test-password-123";
    private static final String EMAIL = "invitee@example.test";
    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    private StaffInvitationPersistence invitations;
    private StaffAccountProvisioningStore accounts;
    private StaffAccountActivationDeliveryStore activationDeliveries;
    private AuthorizedBranchQuery branches;
    private StaffTokenProtector tokenProtector;
    private PasswordEncoder passwordEncoder;
    private StaffInvitationAcceptanceTransactionService service;
    private StaffTokenFingerprint fingerprint;
    private StaffInvitationRecord invitation;
    private AuthorizedBranchSummary branch;

    @BeforeEach
    void setUp() {
        invitations = mock(StaffInvitationPersistence.class);
        accounts = mock(StaffAccountProvisioningStore.class);
        activationDeliveries = mock(StaffAccountActivationDeliveryStore.class);
        branches = mock(AuthorizedBranchQuery.class);
        tokenProtector = mock(StaffTokenProtector.class);
        passwordEncoder = mock(PasswordEncoder.class);
        fingerprint = new StaffTokenFingerprint(
                "b".repeat(64), StaffTokenPurpose.INVITATION.schemeVersion());
        when(tokenProtector.fingerprint(TOKEN, StaffTokenPurpose.INVITATION))
                .thenReturn(fingerprint);
        service = new StaffInvitationAcceptanceTransactionService(
                invitations,
                accounts,
                activationDeliveries,
                branches,
                tokenProtector,
                passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC));
        invitation = invitation(
                StaffInvitationStatus.PENDING,
                NOW.plus(StaffInvitationPolicy.RECEPTIONIST_INVITATION_LIFETIME),
                Set.of(BRANCH_ID),
                RoleCode.RECEPTIONIST,
                StaffScopeType.BRANCH);
        branch = new AuthorizedBranchSummary(
                BRANCH_ID, ORGANIZATION_ID, "NORTH", "North Branch", "America/El_Salvador", false);
    }

    @Test
    void inspectionShowsMaskedIdentityAndOnlyProposedBranchSummary() {
        when(invitations.findPendingByFingerprint(fingerprint)).thenReturn(Optional.of(invitation));
        when(branches.findActiveInvitationBranches(ORGANIZATION_ID, Set.of(BRANCH_ID)))
                .thenReturn(List.of(branch));

        var inspection = service.inspect(TOKEN);

        assertThat(inspection.maskedEmail()).isEqualTo("i***@example.test");
        assertThat(inspection.proposedRole()).isEqualTo(RoleCode.RECEPTIONIST);
        assertThat(inspection.proposedScope()).isEqualTo(StaffScopeType.BRANCH);
        assertThat(inspection.proposedBranches()).extracting(
                io.github.guillermodubon.coachgym.user.StaffInvitationBranchSummary::code)
                .containsExactly("NORTH");
        assertThat(inspection.proposedBranches()).extracting(
                io.github.guillermodubon.coachgym.user.StaffInvitationBranchSummary::name)
                .containsExactly("North Branch");
        assertThat(inspection.toString()).doesNotContain(TOKEN, fingerprint.value(), EMAIL);
        verify(accounts, never()).emailInUse(any());
    }

    @Test
    void acceptanceCreatesBoundAuthorityAndConsumesInvitationInSameUseCase() {
        when(invitations.lockPendingByFingerprint(fingerprint)).thenReturn(Optional.of(invitation));
        when(branches.lockActiveInvitationBranches(ORGANIZATION_ID, Set.of(BRANCH_ID)))
                .thenReturn(List.of(branch));
        when(accounts.emailInUse(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("{bcrypt}encoded-password-hash");
        when(invitations.transition(
                eq(INVITATION_ID), eq(StaffInvitationStatus.ACCEPTED), eq(NOW), any(UUID.class), eq(7L)))
                .thenAnswer(invocation -> invitation(
                        StaffInvitationStatus.ACCEPTED,
                        NOW.plus(StaffInvitationPolicy.RECEPTIONIST_INVITATION_LIFETIME),
                        Set.of(BRANCH_ID), RoleCode.RECEPTIONIST, StaffScopeType.BRANCH));
        AcceptStaffInvitationCommand command = command();

        PreparedStaffInvitationAcceptance accepted = service.accept(command);

        ArgumentCaptor<StaffAccountProvisioningDraft> draft =
                ArgumentCaptor.forClass(StaffAccountProvisioningDraft.class);
        verify(accounts).provision(draft.capture());
        assertThat(draft.getValue().email()).isEqualTo(EMAIL);
        assertThat(draft.getValue().firstName()).isEqualTo("Ada");
        assertThat(draft.getValue().lastName()).isEqualTo("Lovelace");
        assertThat(draft.getValue().passwordHash()).isEqualTo("{bcrypt}encoded-password-hash");
        assertThat(draft.getValue().role()).isEqualTo(RoleCode.RECEPTIONIST);
        assertThat(draft.getValue().scope()).isEqualTo(StaffScopeType.BRANCH);
        assertThat(draft.getValue().branchIds()).containsExactly(BRANCH_ID);
        assertThat(draft.getValue().invitedByUserId()).isEqualTo(INVITER_ID);
        assertThat(draft.getValue().toString()).doesNotContain(
                TOKEN, PASSWORD, EMAIL, "encoded-password-hash");
        assertThat(accepted.result().userId()).isEqualTo(draft.getValue().userId());
        assertThat(accepted.event().toString()).doesNotContain(TOKEN, PASSWORD, EMAIL);
        assertThat(accepted.toString())
                .doesNotContain(TOKEN, PASSWORD, EMAIL, "encoded-password-hash");

        InOrder order = inOrder(invitations, branches, accounts, passwordEncoder, activationDeliveries);
        order.verify(invitations).lockPendingByFingerprint(fingerprint);
        order.verify(branches).lockActiveInvitationBranches(ORGANIZATION_ID, Set.of(BRANCH_ID));
        order.verify(accounts).emailInUse(EMAIL);
        order.verify(passwordEncoder).encode(PASSWORD);
        order.verify(accounts).provision(any(StaffAccountProvisioningDraft.class));
        order.verify(invitations).transition(
                eq(INVITATION_ID), eq(StaffInvitationStatus.ACCEPTED), eq(NOW),
                eq(draft.getValue().userId()), eq(7L));
        order.verify(activationDeliveries).createPending(INVITATION_ID);
    }

    @Test
    void expiredInvitationUsesGenericUnavailableResponseWithoutProvisioning() {
        invitation = invitation(
                StaffInvitationStatus.PENDING,
                NOW,
                Set.of(BRANCH_ID),
                RoleCode.RECEPTIONIST,
                StaffScopeType.BRANCH);
        when(invitations.lockPendingByFingerprint(fingerprint)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.accept(command()))
                .isInstanceOf(StaffIdentityStateConflictException.class)
                .hasMessage("Invitation is not available.");
        verify(accounts, never()).provision(any());
        verify(invitations, never()).transition(any(), any(), any(), any(), anyLong());
    }

    @Test
    void inactiveProposedBranchCannotBeAccepted() {
        when(invitations.lockPendingByFingerprint(fingerprint)).thenReturn(Optional.of(invitation));
        when(branches.lockActiveInvitationBranches(ORGANIZATION_ID, Set.of(BRANCH_ID)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.accept(command()))
                .isInstanceOf(StaffIdentityStateConflictException.class)
                .hasMessage("Invitation is not available.");
        verify(accounts, never()).emailInUse(any());
        verify(accounts, never()).provision(any());
    }

    @Test
    void anExistingAccountCannotClaimTheInvitationEmail() {
        when(invitations.lockPendingByFingerprint(fingerprint)).thenReturn(Optional.of(invitation));
        when(branches.lockActiveInvitationBranches(ORGANIZATION_ID, Set.of(BRANCH_ID)))
                .thenReturn(List.of(branch));
        when(accounts.emailInUse(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.accept(command()))
                .isInstanceOf(StaffIdentityStateConflictException.class)
                .hasMessage("Invitation is not available.");
        verify(passwordEncoder, never()).encode(any());
        verify(accounts, never()).provision(any());
    }

    private static AcceptStaffInvitationCommand command() {
        return new AcceptStaffInvitationCommand(TOKEN, PASSWORD, PASSWORD, "Ada", "Lovelace");
    }

    private static StaffInvitationRecord invitation(
            StaffInvitationStatus status,
            Instant expiresAt,
            Set<UUID> branchIds,
            RoleCode role,
            StaffScopeType scope) {
        return new StaffInvitationRecord(
                INVITATION_ID,
                ORGANIZATION_ID,
                EMAIL,
                role,
                scope,
                branchIds,
                status,
                new StaffTokenFingerprint(
                        "b".repeat(64), StaffTokenPurpose.INVITATION.schemeVersion()),
                NOW.minusSeconds(1),
                NOW.minusSeconds(1),
                expiresAt,
                null,
                null,
                null,
                null,
                INVITER_ID,
                7L);
    }
}
