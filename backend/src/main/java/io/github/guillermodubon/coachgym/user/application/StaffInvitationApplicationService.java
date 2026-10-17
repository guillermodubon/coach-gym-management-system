package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.shared.identityemail.IdentityEmailDeliveryStatus;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffInvitationEmailSender;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.CreateStaffInvitationCommand;
import io.github.guillermodubon.coachgym.user.StaffInvitationCreated;
import io.github.guillermodubon.coachgym.user.StaffInvitationDeliveryAttempted;
import io.github.guillermodubon.coachgym.user.StaffInvitationDeliveryResult;
import io.github.guillermodubon.coachgym.user.StaffInvitationDeliveryStatus;
import io.github.guillermodubon.coachgym.user.StaffInvitationDetails;
import io.github.guillermodubon.coachgym.user.StaffInvitationPageDetails;
import io.github.guillermodubon.coachgym.user.StaffInvitationResent;
import io.github.guillermodubon.coachgym.user.StaffInvitationRevoked;
import io.github.guillermodubon.coachgym.user.StaffInvitationStatus;
import io.github.guillermodubon.coachgym.user.StaffIdentityValuePolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Administrative invitation facade. Email delivery begins only after transactional state commits. */
@Service
public class StaffInvitationApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaffInvitationApplicationService.class);

    private final StaffInvitationTransactionService transactions;
    private final StaffInvitationEmailSender emailSender;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public StaffInvitationApplicationService(
            StaffInvitationTransactionService transactions,
            StaffInvitationEmailSender emailSender,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.transactions = Objects.requireNonNull(transactions);
        this.emailSender = Objects.requireNonNull(emailSender);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Creates an invitation and returns metadata plus a safe delivery outcome, never the token. */
    @PreAuthorize("hasRole('ADMIN')")
    public StaffInvitationDeliveryResult create(
            CreateStaffInvitationCommand command,
            AuthenticatedActor actor,
            String currentPassword) {
        PreparedStaffInvitationDelivery prepared = transactions.create(command, actor, currentPassword);
        StaffInvitationDeliveryStatus status = deliver(prepared);
        var invitation = prepared.invitation();
        publishSafely(new StaffInvitationCreated(
                invitation.invitationId(),
                invitation.organizationId(),
                invitation.invitedByUserId(),
                StaffIdentityValuePolicy.maskEmail(invitation.invitedEmail()),
                invitation.proposedRole(),
                invitation.proposedScope(),
                invitation.proposedBranchIds(),
                invitation.createdAt()));
        return new StaffInvitationDeliveryResult(invitation.safeDetails(), status);
    }

    /** Rotates the token of a pending invitation and performs one bounded delivery attempt. */
    @PreAuthorize("hasRole('ADMIN')")
    public StaffInvitationDeliveryResult resend(
            UUID invitationId,
            long expectedVersion,
            AuthenticatedActor actor) {
        PreparedStaffInvitationDelivery prepared = transactions.resend(
                invitationId, expectedVersion, actor);
        StaffInvitationDeliveryStatus status = deliver(prepared);
        var invitation = prepared.invitation();
        publishSafely(new StaffInvitationResent(
                invitation.invitationId(),
                invitation.organizationId(),
                actor.id(),
                invitation.version(),
                prepared.reservedAt()));
        return new StaffInvitationDeliveryResult(invitation.safeDetails(), status);
    }

    /** Revokes a pending invitation immediately using optimistic version checking. */
    @PreAuthorize("hasRole('ADMIN')")
    public StaffInvitationDetails revoke(
            UUID invitationId,
            long expectedVersion,
            AuthenticatedActor actor) {
        StaffInvitationRecord revokedRecord = transactions.revoke(invitationId, expectedVersion, actor);
        StaffInvitationDetails revoked = revokedRecord.safeDetails();
        publishSafely(new StaffInvitationRevoked(
                revoked.invitationId(),
                revokedRecord.organizationId(),
                actor.id(),
                revoked.version(),
                clock.instant()));
        return revoked;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public StaffInvitationDetails findById(UUID invitationId, AuthenticatedActor actor) {
        return transactions.findById(invitationId, actor);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public StaffInvitationPageDetails findPage(
            StaffInvitationStatus status,
            int page,
            int pageSize,
            AuthenticatedActor actor) {
        return findPage(new StaffInvitationSearchQuery(
                status, null, null, null, null, null, null, null, null,
                page, pageSize, StaffInvitationSortField.CREATED_AT,
                StaffInvitationSortDirection.DESC), actor);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public StaffInvitationPageDetails findPage(
            StaffInvitationSearchQuery query,
            AuthenticatedActor actor) {
        StaffInvitationPage result = transactions.findPage(query, actor);
        return new StaffInvitationPageDetails(
                result.invitations().stream().map(StaffInvitationRecord::safeDetails).toList(),
                result.hasNext());
    }

    private StaffInvitationDeliveryStatus deliver(PreparedStaffInvitationDelivery prepared) {
        IdentityEmailDeliveryStatus senderStatus;
        try {
            senderStatus = emailSender.sendInvitation(prepared.email());
        } catch (RuntimeException failure) {
            senderStatus = IdentityEmailDeliveryStatus.AMBIGUOUS;
        }
        StaffInvitationDeliveryStatus status = mapStatus(senderStatus);
        Instant completedAt = clock.instant();
        if (completedAt.isBefore(prepared.reservedAt())) {
            completedAt = prepared.reservedAt();
        }
        try {
            transactions.completeDelivery(
                    prepared.invitation().invitationId(),
                    prepared.invitation().version(),
                    status,
                    completedAt);
            publishSafely(new StaffInvitationDeliveryAttempted(
                    prepared.invitation().invitationId(),
                    prepared.invitation().version(),
                    status,
                    completedAt));
        } catch (RuntimeException failure) {
            // A persisted RESERVED row remains a conservative attempt. Never retry a raw link
            // after the transport outcome or its durable recording becomes uncertain.
            LOGGER.warn("Staff invitation delivery outcome could not be confirmed.");
            return StaffInvitationDeliveryStatus.AMBIGUOUS;
        }
        return status;
    }

    private void publishSafely(Object event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (RuntimeException failure) {
            LOGGER.warn("A privacy-safe staff invitation event could not be published ({}).",
                    event.getClass().getSimpleName());
        }
    }

    private static StaffInvitationDeliveryStatus mapStatus(IdentityEmailDeliveryStatus status) {
        if (status == null) {
            return StaffInvitationDeliveryStatus.AMBIGUOUS;
        }
        return switch (status) {
            case SENT -> StaffInvitationDeliveryStatus.SENT;
            case FAILED -> StaffInvitationDeliveryStatus.FAILED;
            case AMBIGUOUS -> StaffInvitationDeliveryStatus.AMBIGUOUS;
        };
    }
}
