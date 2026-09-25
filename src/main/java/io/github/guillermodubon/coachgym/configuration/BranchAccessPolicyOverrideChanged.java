package io.github.guillermodubon.coachgym.configuration;

import java.time.Instant;
import java.util.UUID;

/** Privacy-safe event for one versioned branch access-payment policy change. */
public record BranchAccessPolicyOverrideChanged(
        UUID organizationId,
        UUID branchId,
        BranchAccessPaymentPolicyMode previousMode,
        BranchAccessPaymentPolicyMode newMode,
        long version,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {

    public BranchAccessPolicyOverrideChanged {
        if (organizationId == null || branchId == null || actorUserId == null) {
            throw new IllegalArgumentException(
                    "Organization, branch, and actor identifiers are required.");
        }
        if (previousMode == null || newMode == null || previousMode == newMode) {
            throw new IllegalArgumentException(
                    "A branch policy event must represent a mode change.");
        }
        if (version < 1) {
            throw new IllegalArgumentException(
                    "A changed branch policy must have a positive version.");
        }
        if (actorIdentifier == null || actorIdentifier.isBlank() || occurredAt == null) {
            throw new IllegalArgumentException(
                    "Actor identifier and event timestamp are required.");
        }
        actorIdentifier = actorIdentifier.strip();
    }
}
