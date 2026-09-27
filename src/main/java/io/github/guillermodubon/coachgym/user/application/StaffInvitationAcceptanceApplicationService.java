package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.AcceptStaffInvitationCommand;
import io.github.guillermodubon.coachgym.user.StaffInvitationAcceptanceResult;
import io.github.guillermodubon.coachgym.user.StaffInvitationAccepted;
import io.github.guillermodubon.coachgym.user.StaffInvitationInspection;
import io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffIdentityValidationException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** Public application boundary for token-protected invitation inspection and acceptance. */
@Service
public class StaffInvitationAcceptanceApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            StaffInvitationAcceptanceApplicationService.class);

    private final StaffInvitationAcceptanceTransactionService transactions;
    private final ApplicationEventPublisher events;
    private final StaffAccountActivationDeliveryService activationDeliveryService;
    private final StaffIdentityAbuseService abuseControls;

    public StaffInvitationAcceptanceApplicationService(
            StaffInvitationAcceptanceTransactionService transactions,
            ApplicationEventPublisher events,
            StaffAccountActivationDeliveryService activationDeliveryService,
            StaffIdentityAbuseService abuseControls) {
        this.transactions = Objects.requireNonNull(transactions);
        this.events = Objects.requireNonNull(events);
        this.activationDeliveryService = Objects.requireNonNull(activationDeliveryService);
        this.abuseControls = Objects.requireNonNull(abuseControls);
    }

    /** Returns a minimal invitation preview without exposing email or token internals. */
    public StaffInvitationInspection inspect(String presentedToken) {
        return inspect(presentedToken, null);
    }

    /** Caller must pass the server-observed client address, never a forwarded request header. */
    public StaffInvitationInspection inspect(
            String presentedToken,
            String serverObservedClientAddress) {
        if (!abuseControls.invitationAttemptAllowed(presentedToken, serverObservedClientAddress)) {
            throw unavailableInvitation();
        }
        try {
            return transactions.inspect(presentedToken);
        } catch (StaffIdentityStateConflictException | StaffIdentityValidationException unavailable) {
            abuseControls.recordInvitationFailure(presentedToken, serverObservedClientAddress);
            throw unavailableInvitation();
        }
    }

    /** Commits provisioning before publishing the safe event and sending any email. */
    public StaffInvitationAcceptanceResult accept(AcceptStaffInvitationCommand command) {
        return accept(command, null);
    }

    /** Caller must pass the server-observed client address, never a forwarded request header. */
    public StaffInvitationAcceptanceResult accept(
            AcceptStaffInvitationCommand command,
            String serverObservedClientAddress) {
        Objects.requireNonNull(command, "Invitation acceptance command is required.");
        if (!abuseControls.invitationAttemptAllowed(command.token(), serverObservedClientAddress)) {
            throw unavailableInvitation();
        }
        PreparedStaffInvitationAcceptance accepted;
        try {
            accepted = transactions.accept(command);
        } catch (StaffIdentityStateConflictException | StaffIdentityValidationException unavailable) {
            abuseControls.recordInvitationFailure(command.token(), serverObservedClientAddress);
            throw unavailableInvitation();
        }
        publishSafely(accepted.event());
        activationDeliveryService.sendAfterCommit(accepted.event().invitationId());
        return accepted.result();
    }

    /** Package-private hook for a later authorized retry operation. */
    boolean retryActivationNotice(UUID invitationId) {
        return activationDeliveryService.attemptIfDue(invitationId);
    }

    private void publishSafely(StaffInvitationAccepted event) {
        try {
            events.publishEvent(event);
        } catch (RuntimeException failure) {
            LOGGER.warn("A privacy-safe staff invitation acceptance event could not be published.");
        }
    }

    private static StaffIdentityStateConflictException unavailableInvitation() {
        return new StaffIdentityStateConflictException("Invitation is not available.");
    }
}
