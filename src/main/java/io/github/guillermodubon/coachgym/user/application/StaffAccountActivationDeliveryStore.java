package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.shared.identityemail.IdentityEmailDeliveryStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for token-free, durable account-activation notice retry state. */
public interface StaffAccountActivationDeliveryStore {

    /** Creates the pending delivery in the same transaction as account provisioning. */
    void createPending(UUID invitationId);

    /** Atomically claims a due attempt and resolves its message from canonical accepted identity data. */
    Optional<StaffAccountActivationDeliveryClaim> claim(
            UUID invitationId,
            Instant claimedAt,
            Instant leaseExpiresAt);

    /** Completes only the currently leased attempt, retaining no provider message or exception data. */
    void complete(
            UUID deliveryId,
            int attemptNumber,
            IdentityEmailDeliveryStatus outcome,
            Instant completedAt,
            Instant retryAt);
}
