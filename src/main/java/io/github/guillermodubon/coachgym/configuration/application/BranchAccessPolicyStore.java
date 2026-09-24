package io.github.guillermodubon.coachgym.configuration.application;

import io.github.guillermodubon.coachgym.configuration.BranchAccessPaymentPolicyMode;
import io.github.guillermodubon.coachgym.configuration.EffectiveBranchAccessPolicy;
import java.time.Instant;
import java.util.UUID;

/** Persistence port for versioned branch access-payment overrides. */
public interface BranchAccessPolicyStore {

    /**
     * Sets one active canonical branch's mode using optimistic locking.
     *
     * @throws BranchAccessPolicyVersionConflictException if the version is stale
     * @throws BranchAccessPolicyNotFoundException if the active branch is unavailable
     * @throws BranchAccessPolicyDataAccessException if storage fails
     */
    EffectiveBranchAccessPolicy update(
            UUID organizationId,
            UUID branchId,
            BranchAccessPaymentPolicyMode mode,
            long expectedVersion,
            UUID actorUserId,
            Instant occurredAt);
}
