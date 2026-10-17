package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.AcceptStaffInvitationCommand;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchQuery;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffInvitationAcceptanceResult;
import io.github.guillermodubon.coachgym.user.StaffInvitationInspection;
import io.github.guillermodubon.coachgym.user.StaffInvitationBranchSummary;
import io.github.guillermodubon.coachgym.user.StaffInvitationPolicy;
import io.github.guillermodubon.coachgym.user.StaffInvitationStatus;
import io.github.guillermodubon.coachgym.user.StaffIdentityValuePolicy;
import io.github.guillermodubon.coachgym.user.StaffTokenPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundary for safe inspection and all-or-nothing invitation acceptance. */
@Service
class StaffInvitationAcceptanceTransactionService {

    private final StaffInvitationPersistence invitations;
    private final StaffAccountProvisioningStore accounts;
    private final StaffAccountActivationDeliveryStore activationDeliveries;
    private final AuthorizedBranchQuery branches;
    private final StaffTokenProtector tokenProtector;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    StaffInvitationAcceptanceTransactionService(
            StaffInvitationPersistence invitations,
            StaffAccountProvisioningStore accounts,
            StaffAccountActivationDeliveryStore activationDeliveries,
            AuthorizedBranchQuery branches,
            StaffTokenProtector tokenProtector,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.invitations = Objects.requireNonNull(invitations);
        this.accounts = Objects.requireNonNull(accounts);
        this.activationDeliveries = Objects.requireNonNull(activationDeliveries);
        this.branches = Objects.requireNonNull(branches);
        this.tokenProtector = Objects.requireNonNull(tokenProtector);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    StaffInvitationInspection inspect(String presentedToken) {
        String token = StaffTokenPolicy.requirePresentedToken(presentedToken);
        StaffTokenFingerprint fingerprint = tokenProtector.fingerprint(
                token, StaffTokenPurpose.INVITATION);
        StaffInvitationRecord invitation = invitations.findPendingByFingerprint(fingerprint)
                .orElseThrow(StaffInvitationAcceptanceTransactionService::unavailableInvitation);
        Instant now = clock.instant();
        requireUsable(invitation, now);
        List<AuthorizedBranchSummary> activeBranches = activeBranches(invitation, false);
        return new StaffInvitationInspection(
                invitation.invitationId(),
                StaffIdentityValuePolicy.maskEmail(invitation.invitedEmail()),
                invitation.proposedRole(),
                invitation.proposedScope(),
                activeBranches.stream()
                        .map(branch -> new StaffInvitationBranchSummary(
                                branch.id(), branch.code(), branch.name()))
                        .toList(),
                invitation.expiresAt());
    }

    @Transactional
    PreparedStaffInvitationAcceptance accept(AcceptStaffInvitationCommand command) {
        Objects.requireNonNull(command, "Invitation acceptance command is required.");
        String token = StaffTokenPolicy.requirePresentedToken(command.token());
        StaffTokenFingerprint fingerprint = tokenProtector.fingerprint(
                token, StaffTokenPurpose.INVITATION);
        StaffInvitationRecord invitation = invitations.lockPendingByFingerprint(fingerprint)
                .orElseThrow(StaffInvitationAcceptanceTransactionService::unavailableInvitation);
        Instant acceptedAt = clock.instant();
        requireUsable(invitation, acceptedAt);
        List<AuthorizedBranchSummary> activeBranches = activeBranches(invitation, true);
        if (accounts.emailInUse(invitation.invitedEmail())) {
            throw unavailableInvitation();
        }

        String passwordHash = passwordEncoder.encode(command.password());
        UUID userId = UUID.randomUUID();
        accounts.provision(new StaffAccountProvisioningDraft(
                userId,
                invitation.invitedEmail(),
                passwordHash,
                command.firstName(),
                command.lastName(),
                invitation.proposedRole(),
                invitation.proposedScope(),
                invitation.proposedBranchIds(),
                invitation.invitedByUserId(),
                acceptedAt));

        invitations.transition(
                invitation.invitationId(),
                StaffInvitationStatus.ACCEPTED,
                acceptedAt,
                userId,
                invitation.version());

        activationDeliveries.createPending(invitation.invitationId());

        StaffInvitationAcceptanceResult result = new StaffInvitationAcceptanceResult(
                userId,
                invitation.proposedRole(),
                invitation.proposedScope(),
                invitation.proposedBranchIds(),
                acceptedAt);
        var acceptedEvent = new io.github.guillermodubon.coachgym.user.StaffInvitationAccepted(
                invitation.invitationId(),
                invitation.organizationId(),
                userId,
                invitation.invitedByUserId(),
                invitation.proposedRole(),
                invitation.proposedScope(),
                invitation.proposedBranchIds(),
                acceptedAt);
        return new PreparedStaffInvitationAcceptance(result, acceptedEvent);
    }

    private List<AuthorizedBranchSummary> activeBranches(
            StaffInvitationRecord invitation, boolean lock) {
        Set<UUID> proposed = StaffInvitationPolicy.requireValidProposal(
                invitation.proposedRole(),
                invitation.proposedScope(),
                invitation.proposedBranchIds());
        List<AuthorizedBranchSummary> current = lock
                ? branches.lockActiveInvitationBranches(invitation.organizationId(), proposed)
                : branches.findActiveInvitationBranches(invitation.organizationId(), proposed);
        Set<UUID> activeIds = current.stream()
                .map(AuthorizedBranchSummary::id)
                .collect(Collectors.toUnmodifiableSet());
        if (!activeIds.containsAll(proposed)) {
            throw unavailableInvitation();
        }
        return current;
    }

    private static void requireUsable(StaffInvitationRecord invitation, Instant now) {
        if (invitation.status() != StaffInvitationStatus.PENDING
                || now == null
                || !now.isBefore(invitation.expiresAt())) {
            throw unavailableInvitation();
        }
    }

    private static StaffIdentityStateConflictException unavailableInvitation() {
        return new StaffIdentityStateConflictException("Invitation is not available.");
    }
}
