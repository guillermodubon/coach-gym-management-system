package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.shared.identityemail.IdentityEmailDeliveryStatus;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffAccountActivatedEmailSender;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Sends activation notices after commit and preserves bounded, token-free retry state. */
@Service
class StaffAccountActivationDeliveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            StaffAccountActivationDeliveryService.class);

    private final StaffAccountActivationDeliveryStore deliveries;
    private final StaffAccountActivatedEmailSender sender;
    private final StaffAccountActivationRetryPolicy retryPolicy;
    private final Clock clock;

    StaffAccountActivationDeliveryService(
            StaffAccountActivationDeliveryStore deliveries,
            StaffAccountActivatedEmailSender sender,
            StaffAccountActivationRetryPolicy retryPolicy,
            Clock clock) {
        this.deliveries = Objects.requireNonNull(deliveries);
        this.sender = Objects.requireNonNull(sender);
        this.retryPolicy = Objects.requireNonNull(retryPolicy);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Best-effort initial attempt; the committed account and pending outbox survive every failure. */
    void sendAfterCommit(UUID invitationId) {
        try {
            attemptIfDue(invitationId);
        } catch (RuntimeException failure) {
            LOGGER.warn("A post-commit staff account activation notice could not be processed.");
        }
    }

    /** Internal retry entry point for a later authorized application operation. */
    boolean attemptIfDue(UUID invitationId) {
        if (invitationId == null) {
            return false;
        }
        Instant claimedAt = clock.instant();
        var claim = deliveries.claim(
                invitationId,
                claimedAt,
                claimedAt.plus(StaffAccountActivationRetryPolicy.CLAIM_LEASE));
        if (claim.isEmpty()) {
            return false;
        }

        StaffAccountActivationDeliveryClaim current = claim.get();
        IdentityEmailDeliveryStatus outcome;
        try {
            outcome = sender.sendAccountActivated(current.email());
            if (outcome == null) {
                outcome = IdentityEmailDeliveryStatus.AMBIGUOUS;
            }
        } catch (RuntimeException failure) {
            outcome = IdentityEmailDeliveryStatus.AMBIGUOUS;
        }

        Instant completedAt = clock.instant();
        Instant retryAt = outcome == IdentityEmailDeliveryStatus.FAILED
                && current.attemptNumber() < StaffAccountActivationRetryPolicy.MAX_ATTEMPTS
                ? retryPolicy.retryAt(current.attemptNumber(), completedAt)
                : null;
        deliveries.complete(
                current.deliveryId(), current.attemptNumber(), outcome, completedAt, retryAt);
        return outcome == IdentityEmailDeliveryStatus.SENT;
    }
}
