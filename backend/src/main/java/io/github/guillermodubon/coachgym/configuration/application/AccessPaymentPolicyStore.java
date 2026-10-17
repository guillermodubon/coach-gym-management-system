package io.github.guillermodubon.coachgym.configuration.application;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicy;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyDetails;
import java.time.Instant;
import java.util.UUID;

/** Persistence port for versioned updates of the access-payment policy. */
public interface AccessPaymentPolicyStore {

    /**
     * Updates the policy only when {@code expectedVersion} is current.
     *
     * @param policy new typed policy value
     * @param expectedVersion version read by the caller
     * @param actorId authenticated server-side actor responsible for the change
     * @param occurredAt server-side timestamp for the change
     * @return the exact persisted projection after the update
     * @throws AccessPaymentPolicyVersionConflictException when the version is stale
     * @throws AccessPaymentPolicyDataAccessException for unexpected data failures
     */
    AccessPaymentPolicyDetails update(
            AccessPaymentPolicy policy,
            long expectedVersion,
            UUID actorId,
            Instant occurredAt);
}
