package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.shared.identityemail.StaffInvitationEmail;
import io.github.guillermodubon.coachgym.shared.security.CurrentPasswordVerifier;
import io.github.guillermodubon.coachgym.shared.security.StaffIdentityAbuseLimits;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchQuery;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.CreateStaffInvitationCommand;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffIdentityAuthorizationException;
import io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffInvitationDetails;
import io.github.guillermodubon.coachgym.user.StaffInvitationNotFoundException;
import io.github.guillermodubon.coachgym.user.StaffInvitationPolicy;
import io.github.guillermodubon.coachgym.user.StaffInvitationStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeAuthorizationPolicy;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import io.github.guillermodubon.coachgym.user.StaffTokenPolicy;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional invitation state changes; transport work happens in the outer service after commit. */
@Service
class StaffInvitationTransactionService {

    private final StaffInvitationPersistence invitations;
    private final StaffScopeQuery scopeQuery;
    private final AuthorizedBranchQuery branchQuery;
    private final StaffOneTimeTokenGenerator tokenGenerator;
    private final StaffTokenProtector tokenProtector;
    private final CurrentPasswordVerifier currentPasswordVerifier;
    private final Clock clock;
    private final StaffIdentityAbuseLimits abuseLimits;

    @Autowired
    StaffInvitationTransactionService(
            StaffInvitationPersistence invitations,
            StaffScopeQuery scopeQuery,
            AuthorizedBranchQuery branchQuery,
            StaffOneTimeTokenGenerator tokenGenerator,
            StaffTokenProtector tokenProtector,
            CurrentPasswordVerifier currentPasswordVerifier,
            Clock clock,
            StaffIdentityAbuseLimits abuseLimits) {
        this.invitations = Objects.requireNonNull(invitations);
        this.scopeQuery = Objects.requireNonNull(scopeQuery);
        this.branchQuery = Objects.requireNonNull(branchQuery);
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator);
        this.tokenProtector = Objects.requireNonNull(tokenProtector);
        this.currentPasswordVerifier = Objects.requireNonNull(currentPasswordVerifier);
        this.clock = Objects.requireNonNull(clock);
        this.abuseLimits = Objects.requireNonNull(abuseLimits);
    }

    StaffInvitationTransactionService(
            StaffInvitationPersistence invitations,
            StaffScopeQuery scopeQuery,
            AuthorizedBranchQuery branchQuery,
            StaffOneTimeTokenGenerator tokenGenerator,
            StaffTokenProtector tokenProtector,
            CurrentPasswordVerifier currentPasswordVerifier,
            Clock clock) {
        this(invitations, scopeQuery, branchQuery, tokenGenerator, tokenProtector,
                currentPasswordVerifier, clock, StaffIdentityAbuseLimits.defaults());
    }

    @Transactional
    PreparedStaffInvitationDelivery create(
            CreateStaffInvitationCommand command,
            AuthenticatedActor actor,
            String currentPassword) {
        Objects.requireNonNull(command, "Invitation command is required.");
        StaffAuthorizationContext actorContext = requireOrganizationAdmin(actor);
        UUID organizationId = branchQuery.findAuthorizedOrganizationId(actor.id())
                .orElseThrow(() -> new StaffIdentityAuthorizationException(
                        "The actor has no active organization context."));
        List<AuthorizedBranchSummary> activeBranches = branchQuery.findAuthorizedActiveBranches(actor.id())
                .stream()
                .filter(branch -> organizationId.equals(branch.organizationId()))
                .toList();
        Set<UUID> activeBranchIds = activeBranches.stream()
                .map(AuthorizedBranchSummary::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<UUID> proposedBranchIds = StaffInvitationPolicy.requireValidProposal(
                command.proposedRole(), command.proposedScope(), command.proposedBranchIds());
        StaffInvitationPolicy.requireAllBranchesActive(proposedBranchIds, activeBranchIds);

        boolean reauthenticated = !StaffInvitationPolicy.requiresReauthentication(command.proposedRole())
                || currentPasswordVerifier.verify(actor.id(), actor.username(), currentPassword);
        if (StaffInvitationPolicy.requiresReauthentication(command.proposedRole()) && !reauthenticated) {
            throw new StaffCurrentPasswordInvalidException();
        }
        StaffInvitationPolicy.requireCanInvite(
                actorContext,
                command.proposedRole(),
                command.proposedScope(),
                proposedBranchIds,
                activeBranchIds,
                reauthenticated);
        Instant now = clock.instant();
        String token = StaffTokenPolicy.requirePresentedToken(tokenGenerator.generate());
        StaffTokenFingerprint fingerprint = tokenProtector.fingerprint(
                token, StaffTokenPurpose.INVITATION);
        Instant expiresAt = now.plus(StaffInvitationPolicy.defaultLifetime(command.proposedRole()));
        StaffInvitationDraft draft = new StaffInvitationDraft(
                UUID.randomUUID(),
                organizationId,
                command.email(),
                command.proposedRole(),
                command.proposedScope(),
                proposedBranchIds,
                fingerprint,
                now,
                now,
                expiresAt,
                actor.id());
        StaffInvitationRecord created = invitations.create(draft);
        invitations.reserveDeliveryAttempt(created.invitationId(), created.version(), now);
        return prepareDelivery(created, activeBranches, token, now);
    }

    @Transactional
    PreparedStaffInvitationDelivery resend(
            UUID invitationId,
            long expectedVersion,
            AuthenticatedActor actor) {
        StaffAuthorizationContext actorContext = requireOrganizationAdmin(actor);
        UUID organizationId = branchQuery.findAuthorizedOrganizationId(actor.id())
                .orElseThrow(() -> new StaffIdentityAuthorizationException(
                        "The actor has no active organization context."));
        StaffInvitationRecord current = requireInvitation(invitationId, organizationId);
        if (expectedVersion < 0 || current.version() != expectedVersion) {
            throw new StaffIdentityStateConflictException(
                    "Invitation version changed before resend.");
        }
        Instant now = clock.instant();
        StaffInvitationPolicy.requireResendAllowed(
                current.status(), current.lastSentAt(), now, abuseLimits.invitationResendCooldown());
        StaffInvitationPolicy.requireAcceptable(current.status(), now, current.expiresAt());
        List<AuthorizedBranchSummary> activeBranches = branchQuery.findAuthorizedActiveBranches(actor.id())
                .stream()
                .filter(branch -> organizationId.equals(branch.organizationId()))
                .toList();
        Set<UUID> activeBranchIds = activeBranches.stream()
                .map(AuthorizedBranchSummary::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        StaffInvitationPolicy.requireAllBranchesActive(current.proposedBranchIds(), activeBranchIds);
        if (!actorContext.organizationAdmin()) {
            throw new StaffIdentityAuthorizationException(
                    "Only an active organization administrator may manage staff invitations.");
        }

        String token = StaffTokenPolicy.requirePresentedToken(tokenGenerator.generate());
        StaffTokenFingerprint fingerprint = tokenProtector.fingerprint(
                token, StaffTokenPurpose.INVITATION);
        Instant expiresAt = now.plus(StaffInvitationPolicy.defaultLifetime(current.proposedRole()));
        StaffInvitationRecord rotated = invitations.rotatePendingToken(
                invitationId, fingerprint, now, expiresAt, expectedVersion);
        invitations.reserveDeliveryAttempt(rotated.invitationId(), rotated.version(), now);
        return prepareDelivery(rotated, activeBranches, token, now);
    }

    @Transactional
    StaffInvitationRecord revoke(
            UUID invitationId,
            long expectedVersion,
            AuthenticatedActor actor) {
        requireOrganizationAdmin(actor);
        UUID organizationId = branchQuery.findAuthorizedOrganizationId(actor.id())
                .orElseThrow(() -> new StaffIdentityAuthorizationException(
                        "The actor has no active organization context."));
        StaffInvitationRecord current = requireInvitation(invitationId, organizationId);
        if (expectedVersion < 0 || current.version() != expectedVersion) {
            throw new StaffIdentityStateConflictException(
                    "Invitation version changed before revocation.");
        }
        return invitations.transition(
                invitationId, StaffInvitationStatus.REVOKED, clock.instant(), null, expectedVersion);
    }

    @Transactional(readOnly = true)
    StaffInvitationDetails findById(UUID invitationId, AuthenticatedActor actor) {
        requireOrganizationAdmin(actor);
        UUID organizationId = branchQuery.findAuthorizedOrganizationId(actor.id())
                .orElseThrow(() -> new StaffIdentityAuthorizationException(
                        "The actor has no active organization context."));
        return requireInvitation(invitationId, organizationId).safeDetails();
    }

    @Transactional(readOnly = true)
    StaffInvitationPage findPage(
            StaffInvitationStatus status,
            int page,
            int pageSize,
            AuthenticatedActor actor) {
        return findPage(new StaffInvitationSearchQuery(
                status, null, null, null, null, null, null, null, null,
                page, pageSize, StaffInvitationSortField.CREATED_AT,
                StaffInvitationSortDirection.DESC), actor);
    }

    @Transactional(readOnly = true)
    StaffInvitationPage findPage(
            StaffInvitationSearchQuery query,
            AuthenticatedActor actor) {
        Objects.requireNonNull(query, "Invitation search query is required.");
        requireOrganizationAdmin(actor);
        UUID organizationId = branchQuery.findAuthorizedOrganizationId(actor.id())
                .orElseThrow(() -> new StaffIdentityAuthorizationException(
                        "The actor has no active organization context."));
        return invitations.findPage(new StaffInvitationPageQuery(
                organizationId, query.status(), query.role(), query.scope(), query.branchId(),
                query.emailQuery(), query.createdFrom(), query.createdTo(),
                query.expiresFrom(), query.expiresTo(), query.page(), query.pageSize(),
                query.sort(), query.direction()));
    }

    @Transactional
    void completeDelivery(
            UUID invitationId,
            long invitationVersion,
            io.github.guillermodubon.coachgym.user.StaffInvitationDeliveryStatus status,
            Instant completedAt) {
        invitations.completeDeliveryAttempt(
                invitationId, invitationVersion, status, completedAt);
    }

    private StaffAuthorizationContext requireOrganizationAdmin(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null) {
            throw new StaffIdentityAuthorizationException("An authenticated actor is required.");
        }
        StaffAuthorizationContext context = scopeQuery.findAuthorizationContext(actor.id())
                .orElseThrow(() -> new StaffIdentityAuthorizationException(
                        "The actor has no active staff authorization context."));
        StaffScopeAuthorizationPolicy.requireOrganizationAdministrator(context);
        return context;
    }

    private StaffInvitationRecord requireInvitation(UUID invitationId, UUID organizationId) {
        if (invitationId == null) {
            throw new StaffInvitationNotFoundException();
        }
        StaffInvitationRecord invitation = invitations.findById(invitationId)
                .orElseThrow(StaffInvitationNotFoundException::new);
        if (!organizationId.equals(invitation.organizationId())) {
            throw new StaffInvitationNotFoundException();
        }
        return invitation;
    }

    private static PreparedStaffInvitationDelivery prepareDelivery(
            StaffInvitationRecord invitation,
            List<AuthorizedBranchSummary> activeBranches,
            String token,
            Instant reservedAt) {
        Set<UUID> proposedIds = invitation.proposedBranchIds();
        List<String> branchNames = activeBranches.stream()
                .filter(branch -> proposedIds.contains(branch.id()))
                .sorted(java.util.Comparator.comparing(AuthorizedBranchSummary::id))
                .map(AuthorizedBranchSummary::name)
                .toList();
        return new PreparedStaffInvitationDelivery(
                invitation,
                new StaffInvitationEmail(
                        invitation.invitedEmail(),
                        invitation.proposedRole().name(),
                        invitation.proposedScope().name(),
                        branchNames,
                        invitation.expiresAt(),
                        token),
                reservedAt);
    }
}
